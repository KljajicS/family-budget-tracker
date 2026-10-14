package rs.budget.data

import rs.budget.data.db.BudgetDb
import rs.budget.domain.AccountArchived
import rs.budget.domain.AccountCreated
import rs.budget.domain.BudgetRemoved
import rs.budget.domain.BudgetSet
import rs.budget.domain.CategoryCreated
import rs.budget.domain.EventRecord
import rs.budget.domain.MemberCreated
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited

/** Only writer of read tables. Idempotent per record; later `occurredAt` wins for edits. */
class Projector(private val db: BudgetDb) {

    fun apply(record: EventRecord) {
        val at = record.occurredAt.toEpochMilli()
        when (val e = record.event) {
            is MemberCreated -> db.memberQueries.upsert(e.member.id, e.member.displayName, e.member.deviceId)
            is AccountCreated -> db.accountQueries.upsert(
                e.account.id, e.account.name, e.account.currency.name, e.account.kind.name, if (e.account.archived) 1 else 0,
            )
            is AccountArchived -> db.accountQueries.archive(e.accountId)
            is CategoryCreated -> db.categoryQueries.upsert(
                e.category.id, e.category.name, e.category.color, if (e.category.archived) 1 else 0,
            )
            is TransactionAdded -> upsertTx(e.transaction, at)
            is TransactionEdited -> {
                val current = db.txQueries.editedAt(e.transaction.id).executeAsOneOrNull()
                if (current == null || current <= at) upsertTx(e.transaction, at)
            }
            is TransactionDeleted -> db.txQueries.delete(e.transactionId)
            is BudgetSet -> db.budgetQueries.upsert(
                e.budget.categoryId, e.budget.month.toString(), e.budget.money.amount.toPlainString(), e.budget.money.currency.name,
            )
            is BudgetRemoved -> db.budgetQueries.delete(e.categoryId, e.month.toString())
        }
    }

    fun clear() {
        db.budgetQueries.deleteAll()
        db.txQueries.deleteAll()
        db.accountQueries.deleteAll()
        db.categoryQueries.deleteAll()
        db.memberQueries.deleteAll()
    }

    private fun upsertTx(t: Transaction, at: Long) = db.txQueries.upsert(
        id = t.id,
        type = t.type.name,
        amount = t.money.amount.toPlainString(),
        currency = t.money.currency.name,
        account_id = t.accountId,
        to_account_id = t.toAccountId,
        category_id = t.categoryId,
        date = t.date.toString(),
        note = t.note,
        merchant = t.merchant,
        paid_by = t.paidBy,
        source = t.source.name,
        edited_at = at,
    )
}
