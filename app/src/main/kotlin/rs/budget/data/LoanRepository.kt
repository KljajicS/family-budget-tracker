package rs.budget.data

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import rs.budget.data.db.BudgetDb
import rs.budget.domain.*
import java.time.LocalDate

data class LoanBook(
    val loans: List<Loan>,
    val payments: List<LoanPayment>,
    val adjustments: List<LoanAdjustment>,
    val transactions: List<Transaction>,
) {
    fun balance(loan: Loan, today: LocalDate): LoanBalance = LoanCalculator.balance(loan, payments, adjustments, transactions, today)
}

class LoanRepository(private val db: BudgetDb, private val store: EventStore, private val clock: Clock) {
    val book: Flow<LoanBook> = db.loanQueries.observe().asFlow().map {
        db.transactionWithResult { readBook() }
    }.flowOn(Dispatchers.IO)

    private fun readBook() = LoanBook(
        db.loanQueries.all().executeAsList().map { EventJson.decodeFromString<Loan>(it.payload).copy(archived = it.archived != 0L) },
        db.loanPaymentQueries.all().executeAsList().mapNotNull { it.payload?.let { json -> EventJson.decodeFromString<LoanPayment>(json) } },
        db.loanAdjustmentQueries.all().executeAsList().map { EventJson.decodeFromString<LoanAdjustment>(it.payload) },
        db.txQueries.all().executeAsList().map { row ->
            Transaction(row.id, TransactionType.valueOf(row.type), Money.of(row.amount, Currency.valueOf(row.currency)), row.account_id,
                row.to_account_id, row.category_id, LocalDate.parse(row.date), row.note, row.merchant, row.paid_by, TransactionSource.valueOf(row.source))
        },
    )

    private fun loan(id: String): Loan = db.loanQueries.byId(id).executeAsOneOrNull()?.let {
        EventJson.decodeFromString<Loan>(it.payload).copy(archived = it.archived != 0L)
    } ?: error("Loan not found")

    internal fun requireUnlinked(transactionId: String) {
        require(db.loanPaymentQueries.forTransaction(transactionId).executeAsOneOrNull() == null &&
            db.loanReceiptQueries.forTransaction(transactionId).executeAsOneOrNull() == null) { "Use loan payment actions" }
    }

    private fun validateCash(tx: Transaction) {
        val account = db.accountQueries.byId(tx.accountId).executeAsOneOrNull()
        require(account != null && account.archived == 0L && account.currency == tx.money.currency.name) { "Account no longer available" }
        require(tx.date <= clock.today()) { "Payment has not happened yet" }
        if (tx.categoryId != null) require(db.categoryQueries.active().executeAsList().any { it.id == tx.categoryId })
    }

    suspend fun create(loan: Loan, receipt: Transaction? = null) {
        val events = listOf(LoanCreated(loan)) + if (receipt == null) emptyList() else listOf(
            TransactionAdded(receipt), LoanReceiptLinked(loan.id, loan.id, receipt.id),
        )
        store.appendAll(events) {
            require(!loan.archived && loan.start <= clock.today())
            val existing = db.loanQueries.byId(loan.id).executeAsOneOrNull()
            if (existing != null) {
                require(loan(loan.id) == loan)
                val link = db.loanReceiptQueries.all().executeAsList().find { it.loan_id == loan.id }
                require(link?.tx_id == receipt?.id)
                if (receipt != null) require(readBook().transactions.find { it.id == receipt.id } == receipt)
            } else if (receipt != null) {
                validateCash(receipt)
                require(receipt.type == TransactionType.LOAN_RECEIPT && receipt.source == TransactionSource.MANUAL && receipt.date == loan.start)
                require(!loan.opening.isZero)
                requireUnlinked(receipt.id)
                require(readBook().transactions.none { it.id == receipt.id })
            }
        }
    }

    suspend fun update(value: Loan) {
        store.append(LoanUpdated(value)) {
            val old = loan(value.id)
            require(!old.archived && old.opening == value.opening && old.start == value.start && !value.archived)
        }
    }

    suspend fun archive(id: String) { store.append(LoanArchived(id)) { loan(id) } }

    suspend fun record(payment: LoanPayment, transaction: Transaction, linkExisting: Boolean = false) {
        val events = (if (linkExisting) emptyList() else listOf(TransactionAdded(transaction))) + LoanPaymentAdded(payment)
        store.appendAll(events) {
            val book = readBook()
            val target = loan(payment.loanId)
            require(!target.archived && transaction.type == TransactionType.EXPENSE && transaction.id == payment.transactionId)
            require(transaction.date >= target.start && transaction.date <= clock.today())
            val prior = db.loanPaymentQueries.byId(payment.id).executeAsOneOrNull()
            val existingTx = book.transactions.find { it.id == transaction.id }
            if (prior != null) {
                require(prior.removed == 0L && prior.payload?.let { EventJson.decodeFromString<LoanPayment>(it) } == payment && existingTx == transaction)
            } else {
                requireUnlinked(transaction.id)
                if (linkExisting) require(existingTx == transaction) else {
                    require(existingTx == null)
                    validateCash(transaction)
                }
                validateBalance(target, book.copy(payments = book.payments + payment,
                    transactions = book.transactions.filterNot { it.id == transaction.id } + transaction))
            }
        }
    }

    suspend fun split(paymentId: String, principal: Money?) {
        val book = kotlinx.coroutines.withContext(Dispatchers.IO) { db.transactionWithResult { readBook() } }
        val old = book.payments.find { it.id == paymentId } ?: error("Payment not found")
        val changed = old.copy(principal = principal)
        store.append(LoanPaymentUpdated(changed)) {
            val current = readBook()
            require(current.payments.find { it.id == old.id } == old) { "Payment changed" }
            validateBalance(loan(old.loanId), current.copy(payments = current.payments.map { if (it.id == old.id) changed else it }))
        }
    }

    suspend fun removePayment(paymentId: String, deleteExpense: Boolean) {
        val book = kotlinx.coroutines.withContext(Dispatchers.IO) { db.transactionWithResult { readBook() } }
        val old = book.payments.find { it.id == paymentId } ?: error("Payment not found")
        val events = listOf(LoanPaymentRemoved(paymentId)) + if (deleteExpense) listOf(TransactionDeleted(old.transactionId)) else emptyList()
        store.appendAll(events) {
            val current = readBook()
            require(current.payments.find { it.id == old.id } == old) { "Payment changed" }
            validateBalance(loan(old.loanId), current.copy(payments = current.payments.filterNot { it.id == old.id }))
        }
    }

    suspend fun adjust(adjustment: LoanAdjustment) {
        store.append(LoanPrincipalAdjusted(adjustment)) {
            val target = loan(adjustment.loanId)
            require(!target.archived && adjustment.date >= target.start && adjustment.date <= clock.today())
            val current = readBook()
            val prior = current.adjustments.find { it.id == adjustment.id }
            if (prior != null) require(prior == adjustment) else {
                require(current.balance(target, clock.today()).complete) { "Complete payment splits first" }
                validateBalance(target, current.copy(adjustments = current.adjustments + adjustment))
            }
        }
    }

    private fun validateBalance(loan: Loan, book: LoanBook) {
        require(!book.balance(loan, clock.today()).invalidHistory) { "Payment exceeds principal or conflicts with loan history" }
    }
}
