package rs.budget.data

import rs.budget.data.db.BudgetDb
import rs.budget.domain.AccountArchived
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountUpdated
import rs.budget.domain.BillSkipped
import rs.budget.domain.BudgetRemoved
import rs.budget.domain.BudgetSet
import rs.budget.domain.CategoryArchived
import rs.budget.domain.CategoryCreated
import rs.budget.domain.CategoryUpdated
import rs.budget.domain.MemberRenamed
import rs.budget.domain.EventRecord
import rs.budget.domain.MemberCreated
import rs.budget.domain.RecurringRuleArchived
import rs.budget.domain.RecurringRuleSet
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited

/**
 * Only writer of read tables. Correct only when records arrive in log order (see ADR 8):
 * a create never overwrites, an edit only touches an existing row, a delete is final.
 */
class Projector(private val db: BudgetDb) {

    fun apply(record: EventRecord) {
        val at = record.occurredAt.toEpochMilli()
        when (val e = record.event) {
            is MemberCreated -> db.memberQueries.insertIgnore(e.member.id, e.member.displayName, e.member.deviceId)
            is MemberRenamed -> db.memberQueries.rename(e.displayName, e.memberId)
            is AccountCreated -> db.accountQueries.insertIgnore(
                e.account.id, e.account.name, e.account.currency.name, e.account.kind.name, if (e.account.archived) 1 else 0,
            )
            is AccountUpdated -> db.accountQueries.update(
                e.account.name, e.account.currency.name, e.account.kind.name, if (e.account.archived) 1 else 0, e.account.id,
            )
            is AccountArchived -> db.accountQueries.archive(e.accountId)
            is CategoryCreated -> db.categoryQueries.insertIgnore(
                e.category.id, e.category.name, e.category.color, if (e.category.archived) 1 else 0,
            )
            is CategoryUpdated -> db.categoryQueries.update(e.category.name, e.category.color, if (e.category.archived) 1 else 0, e.category.id)
            is CategoryArchived -> db.categoryQueries.archive(e.categoryId)
            is TransactionAdded -> with(e.transaction) {
                db.txQueries.insertIgnore(
                    id, type.name, money.amount.toPlainString(), money.currency.name, accountId, toAccountId, categoryId,
                    date.toString(), note, merchant, paidBy, source.name, at,
                )
            }
            is TransactionEdited -> with(e.transaction) {
                db.txQueries.update(
                    type.name, money.amount.toPlainString(), money.currency.name, accountId, toAccountId, categoryId,
                    date.toString(), note, merchant, paidBy, source.name, at, id,
                )
            }
            is TransactionDeleted -> db.txQueries.delete(e.transactionId)
            is BudgetSet -> db.budgetQueries.upsert(
                e.budget.categoryId, e.budget.month.toString(), e.budget.money.amount.toPlainString(), e.budget.money.currency.name,
            )
            // A zero row is the tombstone: it stops carry-forward from an earlier month (see BudgetCalculator.effective).
            is BudgetRemoved -> db.budgetQueries.upsert(e.categoryId, e.month.toString(), "0.00", "RSD")
            is RecurringRuleSet -> with(e.rule) {
                db.recurringRuleQueries.upsert(
                    id, name, money.amount.toPlainString(), money.currency.name, accountId, categoryId, cadence.name, firstDue.toString(), if (archived) 1 else 0,
                )
            }
            is RecurringRuleArchived -> db.recurringRuleQueries.archive(e.ruleId)
            is BillSkipped -> db.billSkipQueries.insertIgnore(e.ruleId, e.period)
        }
    }

    fun clear() {
        db.billSkipQueries.deleteAll()
        db.recurringRuleQueries.deleteAll()
        db.budgetQueries.deleteAll()
        db.txQueries.deleteAll()
        db.accountQueries.deleteAll()
        db.categoryQueries.deleteAll()
        db.memberQueries.deleteAll()
    }

    companion object {
        /** Bump when a projector change must reach existing installs; the app rebuilds once on mismatch. */
        const val VERSION = 2
    }
}
