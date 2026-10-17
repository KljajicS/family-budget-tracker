package rs.budget.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import rs.budget.data.db.Account as AccountRow
import rs.budget.data.db.BudgetDb
import rs.budget.data.db.Category as CategoryRow
import rs.budget.data.db.Member as MemberRow
import rs.budget.data.db.Goal as GoalRow
import rs.budget.data.db.Recent
import rs.budget.data.db.Recurring_rule as RuleRow
import rs.budget.data.db.Tx as TxRow
import rs.budget.domain.Account
import rs.budget.domain.AccountArchived
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.AccountUpdated
import rs.budget.domain.AccountsReordered
import rs.budget.domain.Bill
import rs.budget.domain.BillCalculator
import rs.budget.domain.BillSkipped
import rs.budget.domain.Cadence
import rs.budget.domain.Goal
import rs.budget.domain.GoalArchived
import rs.budget.domain.GoalCalculator
import rs.budget.domain.GoalSet
import rs.budget.domain.GoalsReordered
import rs.budget.domain.RecurringRule
import rs.budget.domain.RecurringRuleArchived
import rs.budget.domain.RecurringRuleSet
import rs.budget.domain.CategoryArchived
import rs.budget.domain.CategoryUpdated
import rs.budget.domain.MemberRenamed
import rs.budget.domain.Category
import rs.budget.domain.CategoryCreated
import rs.budget.domain.Currency
import rs.budget.domain.HomeCalculator
import rs.budget.domain.HomeSummary
import rs.budget.domain.Member
import rs.budget.domain.MemberCreated
import rs.budget.domain.Money
import rs.budget.domain.ExchangeRateSnapshot
import rs.budget.data.rates.RateRepository
import rs.budget.domain.Receipt
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.newId
import java.time.LocalDate

data class RecentTx(val transaction: Transaction, val categoryName: String?, val paidByName: String?)
class DuplicateReceipt : IllegalStateException("Receipt already saved")
class InvalidReceiptAccount : IllegalStateException("Receipt requires an active RSD account")
class InvalidReceiptCategory : IllegalStateException("Category no longer available")

class BudgetRepository(
    private val db: BudgetDb,
    private val store: EventStore,
    private val clock: Clock,
    private val deviceId: String = "",
    sharingScope: CoroutineScope? = null,
    val exchangeRates: RateRepository = RateRepository(db, store, clock),
) {
    val loans = LoanRepository(db, store, clock)
    /** Active accounts in the family's saved order; pickers and Home use the same order. */
    val accounts: Flow<List<Account>> =
        db.accountQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    val categories: Flow<List<Category>> =
        db.categoryQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    /** Archived rows included: history screens need the names and balances of things that no longer exist. */
    val allAccounts: Flow<List<Account>> =
        db.accountQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    val allCategories: Flow<List<Category>> =
        db.categoryQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    val members: Flow<List<Member>> =
        db.memberQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    val transactions: Flow<List<Transaction>> =
        db.txQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)
            .let { source -> if (sharingScope == null) source else source.shareIn(sharingScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1) }

    fun recent(limit: Int): Flow<List<RecentTx>> =
        db.txQueries.recent(limit.toLong()).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toRecent() } }.flowOn(Dispatchers.Default)

    /** Re-reads the date once a minute so a screen left open over midnight on the 1st moves to the new month. */
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(clock.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val rules: Flow<List<RecurringRule>> =
        db.recurringRuleQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    private val skips: Flow<Set<Pair<String, String>>> =
        db.billSkipQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.rule_id to it.due_period }.toSet() }

    /** Active goals in priority order: savings fill the first one before the next. */
    val goals: Flow<List<Goal>> =
        db.goalQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    private val financialInputs = combine(today, allAccounts, transactions, exchangeRates.snapshots) { day, accs, txs, rates ->
        FinancialInputs(day, accs, txs, rates)
    }
    private val commitments = combine(rules, skips, goals) { rs, ss, gs -> Commitments(rs, ss, gs) }

    val financial: Flow<FinancialSnapshot> = financialInputs.combine(commitments) { input, commitments ->
        val (day, allAccs, txs, exchange) = input
        val rates = exchange.rates
        val active = allAccs.filterNot { it.archived }
        val bills = BillCalculator.upcoming(day, commitments.rules, active.map { it.id }.toSet(), txs, commitments.skips)
        val home = HomeCalculator.summarize(day, allAccs, txs, rates)
        val goals = GoalCalculator.progress(commitments.goals, home.savingsRsd, rates)
        val missing = (active.map { it.currency } + txs.map { it.money.currency } + bills.map { it.rule.money.currency } +
            commitments.goals.map { it.target.currency }).filter { rates.rate(it) == null }.toSet()
        FinancialSnapshot(day, allAccs, txs, bills, goals, home, exchange, missing)
    }.flowOn(Dispatchers.Default).let { source ->
        if (sharingScope == null) source else source.shareIn(sharingScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)
    }

    val home: Flow<HomeSummary> = financial.map { it.home }
    val upcomingBills: Flow<List<Bill>> = financial.map { it.bills }

    private data class FinancialInputs(val day: LocalDate, val accounts: List<Account>, val transactions: List<Transaction>, val rates: ExchangeRateSnapshot)
    private data class Commitments(val rules: List<RecurringRule>, val skips: Set<Pair<String, String>>, val goals: List<Goal>)

    suspend fun goal(id: String): Goal? = withContext(Dispatchers.IO) { db.goalQueries.byId(id).executeAsOneOrNull()?.toDomain() }
    suspend fun setGoal(goal: Goal) { store.append(GoalSet(goal)) }
    suspend fun archiveGoal(id: String) { store.append(GoalArchived(id)) }
    suspend fun reorderGoals(ids: List<String>) { store.append(GoalsReordered(ids)) }

    suspend fun rule(id: String): RecurringRule? = withContext(Dispatchers.IO) { db.recurringRuleQueries.byId(id).executeAsOneOrNull()?.toDomain() }
    suspend fun setRule(rule: RecurringRule) {
        store.append(RecurringRuleSet(rule)) {
            val previous = db.recurringRuleQueries.byId(rule.id).executeAsOneOrNull()
            require(previous == null || (previous.cadence == rule.cadence.name && previous.first_due == rule.firstDue.toString())) {
                "Existing recurring schedules cannot change"
            }
        }
    }
    suspend fun archiveRule(id: String) { store.append(RecurringRuleArchived(id)) }
    suspend fun skipBill(ruleId: String, period: String) { store.append(BillSkipped(ruleId, period)) }

    suspend fun currentMember(): Member? = withContext(Dispatchers.IO) {
        db.memberQueries.byDevice(deviceId).executeAsOneOrNull()?.toDomain()
            ?: db.memberQueries.all().executeAsList().firstOrNull()?.toDomain()
    }

    suspend fun setUp(displayName: String, accounts: List<Account>) {
        store.appendAll(
            listOf(MemberCreated(Member(newId(), displayName, deviceId))) +
                StarterCategories.all.map { CategoryCreated(it) } + accounts.map { AccountCreated(it) },
        ) { check(db.memberQueries.all().executeAsList().isEmpty()) { "Setup already completed" } }
    }

    suspend fun addCategory(name: String, color: String) { store.append(CategoryCreated(Category(newId(), name.trim(), color))) }
    suspend fun updateCategory(category: Category) { store.append(CategoryUpdated(category)) }
    suspend fun archiveCategory(id: String) { store.append(CategoryArchived(id)) }

    suspend fun addAccount(account: Account) { store.append(AccountCreated(account)) }
    suspend fun accountInUse(id: String): Boolean = withContext(Dispatchers.IO) { hasAccountReferences(id) }

    private fun hasAccountReferences(id: String): Boolean =
        db.txQueries.all().executeAsList().any { it.account_id == id || it.to_account_id == id } ||
            db.recurringRuleQueries.active().executeAsList().any { it.account_id == id }

    suspend fun updateAccount(account: Account) {
        store.append(AccountUpdated(account)) {
            val previous = db.accountQueries.byId(account.id).executeAsOneOrNull()
            require(previous == null || previous.currency == account.currency.name || !hasAccountReferences(account.id)) {
                "Used account currency cannot change"
            }
        }
    }

    /** Allowed with money still in it: the balance leaves savings and goals, its transactions stay in reports. */
    suspend fun archiveAccount(id: String) { store.append(AccountArchived(id)) }
    suspend fun reorderAccounts(ids: List<String>) { store.append(AccountsReordered(ids)) }

    suspend fun renameMember(memberId: String, displayName: String) { store.append(MemberRenamed(memberId, displayName.trim())) }

    suspend fun add(t: Transaction) { store.append(TransactionAdded(t)) { loans.requireUnlinked(t.id); require(t.type != TransactionType.LOAN_RECEIPT); validateTransaction(t) } }

    suspend fun hasReceipt(id: String): Boolean = withContext(Dispatchers.IO) {
        db.receiptQueries.byId(id).executeAsOneOrNull() != null
    }

    suspend fun receiptCategory(issuer: String): String? = withContext(Dispatchers.IO) {
        db.receiptQueries.suggestCategory(issuer).executeAsOneOrNull()?.category_id
    }

    suspend fun addReceipt(t: Transaction, receipt: Receipt) {
        require(t.id == receipt.transactionId && t.source == TransactionSource.RECEIPT && t.type == TransactionType.EXPENSE)
        require(t.money.currency == Currency.RSD && receipt.total.currency == Currency.RSD)
        store.append(TransactionAdded(t, receipt)) {
            loans.requireUnlinked(t.id)
            if (db.receiptQueries.byId(receipt.id).executeAsOneOrNull() != null) throw DuplicateReceipt()
            val account = db.accountQueries.byId(t.accountId).executeAsOneOrNull()
            if (account == null || account.archived != 0L || account.currency != "RSD") throw InvalidReceiptAccount()
            if (t.categoryId != null && db.categoryQueries.active().executeAsList().none { it.id == t.categoryId }) {
                throw InvalidReceiptCategory()
            }
        }
    }
    suspend fun edit(t: Transaction) { store.append(TransactionEdited(t)) { loans.requireUnlinked(t.id); require(t.type != TransactionType.LOAN_RECEIPT); validateTransaction(t) } }

    private fun validateTransaction(t: Transaction) {
        val account = db.accountQueries.byId(t.accountId).executeAsOneOrNull()
        require(account != null && account.archived == 0L && account.currency == t.money.currency.name) { "Account no longer available" }
        if (t.categoryId != null) require(db.categoryQueries.active().executeAsList().any { it.id == t.categoryId }) { "Category no longer available" }
        if (t.type == TransactionType.TRANSFER) {
            val destination = t.toAccountId?.let { db.accountQueries.byId(it).executeAsOneOrNull() }
            require(destination != null && destination.archived == 0L && destination.id != account.id && destination.currency == account.currency) {
                "Transfer account no longer available"
            }
        }
    }
    suspend fun delete(id: String) { store.append(TransactionDeleted(id)) { loans.requireUnlinked(id) } }

    private fun GoalRow.toDomain() = Goal(id, name, Money.of(target, Currency.valueOf(currency)), Money.of(monthly, Currency.valueOf(currency)), archived == 1L)
    private fun RuleRow.toDomain() = RecurringRule(
        id, name, Money.of(amount, Currency.valueOf(currency)), account_id, category_id, Cadence.valueOf(cadence), LocalDate.parse(first_due), archived == 1L,
    )
    private fun AccountRow.toDomain() = Account(id, name, Currency.valueOf(currency), AccountKind.valueOf(kind), archived == 1L)
    private fun CategoryRow.toDomain() = Category(id, name, color, archived == 1L)
    private fun MemberRow.toDomain() = Member(id, display_name, device_id)
    private fun TxRow.toDomain() = Transaction(
        id, TransactionType.valueOf(type), Money.of(amount, Currency.valueOf(currency)), account_id, to_account_id,
        category_id, LocalDate.parse(date), note, merchant, paid_by, TransactionSource.valueOf(source),
    )
    private fun Recent.toRecent() = RecentTx(
        Transaction(
            id, TransactionType.valueOf(type), Money.of(amount, Currency.valueOf(currency)), account_id, to_account_id,
            category_id, LocalDate.parse(date), note, merchant, paid_by, TransactionSource.valueOf(source),
        ),
        category_name, paid_by_name,
    )
}
