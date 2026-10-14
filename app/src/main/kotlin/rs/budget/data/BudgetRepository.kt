package rs.budget.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import rs.budget.data.db.Account as AccountRow
import rs.budget.data.db.BudgetDb
import rs.budget.data.db.Category as CategoryRow
import rs.budget.data.db.Member as MemberRow
import rs.budget.data.db.Recent
import rs.budget.data.db.Tx as TxRow
import rs.budget.domain.Account
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.Category
import rs.budget.domain.CategoryCreated
import rs.budget.domain.Currency
import rs.budget.domain.HomeCalculator
import rs.budget.domain.HomeSummary
import rs.budget.domain.Member
import rs.budget.domain.MemberCreated
import rs.budget.domain.Money
import rs.budget.domain.Rates
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.newId
import java.math.BigDecimal
import java.time.LocalDate

data class RecentTx(val transaction: Transaction, val categoryName: String?, val paidByName: String?)

class BudgetRepository(
    private val db: BudgetDb,
    private val store: EventStore,
    private val clock: Clock,
    private val deviceId: String = "",
) {
    val accounts: Flow<List<Account>> =
        db.accountQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    val categories: Flow<List<Category>> =
        db.categoryQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    val members: Flow<List<Member>> =
        db.memberQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    val transactions: Flow<List<Transaction>> =
        db.txQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    fun recent(limit: Int): Flow<List<RecentTx>> =
        db.txQueries.recent(limit.toLong()).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toRecent() } }

    // ponytail: rates are RSD-only until milestone 2 adds the NBS fetch; EUR/USD accounts show as partial.
    val home: Flow<HomeSummary> = combine(accounts, transactions) { accs, txs ->
        HomeCalculator.summarize(clock.today(), accs, txs, Rates(mapOf(Currency.RSD to BigDecimal.ONE)))
    }

    suspend fun currentMember(): Member? = withContext(Dispatchers.IO) {
        db.memberQueries.byDevice(deviceId).executeAsOneOrNull()?.toDomain()
            ?: db.memberQueries.all().executeAsList().firstOrNull()?.toDomain()
    }

    suspend fun setUp(displayName: String, accounts: List<Account>) {
        store.append(MemberCreated(Member(newId(), displayName, deviceId)))
        StarterCategories.all.forEach { store.append(CategoryCreated(it)) }
        accounts.forEach { store.append(AccountCreated(it)) }
    }

    suspend fun add(t: Transaction) { store.append(TransactionAdded(t)) }
    suspend fun edit(t: Transaction) { store.append(TransactionEdited(t)) }
    suspend fun delete(id: String) { store.append(TransactionDeleted(id)) }

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
