package rs.budget.data

import rs.budget.data.db.BudgetDb
import kotlinx.serialization.encodeToString
import rs.budget.domain.EventJson
import rs.budget.domain.AccountArchived
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountUpdated
import rs.budget.domain.BillSkipped
import rs.budget.domain.BudgetRemoved
import rs.budget.domain.BudgetSet
import rs.budget.domain.CategoryArchived
import rs.budget.domain.CategoryCreated
import rs.budget.domain.CategoryUpdated
import rs.budget.domain.GoalAllocated
import rs.budget.domain.GoalAllocationDeleted
import rs.budget.domain.GoalArchived
import rs.budget.domain.GoalSet
import rs.budget.domain.MemberRenamed
import rs.budget.domain.EventRecord
import rs.budget.domain.ExchangeRateOverrideSet
import rs.budget.domain.MemberCreated
import rs.budget.domain.RecurringRuleArchived
import rs.budget.domain.RecurringRuleSet
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited

// Replay in log order so older edits cannot overwrite newer changes.
class Projector(private val db: BudgetDb) {

    fun apply(record: EventRecord) {
        val at = record.occurredAt.toEpochMilli()
        when (val e = record.event) {
            is ExchangeRateOverrideSet -> db.exchangeRateOverrideQueries.upsert(e.currency.name, e.rate, at)
            is MemberCreated -> db.memberQueries.insertIgnore(e.member.id, e.member.displayName, e.member.deviceId)
            is MemberRenamed -> db.memberQueries.rename(e.displayName, e.memberId)
            is AccountCreated -> db.accountQueries.insertIgnore(
                e.account.id, e.account.name, e.account.currency.name, e.account.kind.name, if (e.account.archived) 1 else 0,
            )
            // Edits never change archived: a stale edit from the other phone must not bring a deleted row back (ADR 8).
            is AccountUpdated -> db.accountQueries.update(e.account.name, e.account.currency.name, e.account.kind.name, e.account.id)
            is AccountArchived -> db.accountQueries.archive(e.accountId)
            is CategoryCreated -> db.categoryQueries.insertIgnore(
                e.category.id, e.category.name, e.category.color, if (e.category.archived) 1 else 0,
            )
            is CategoryUpdated -> db.categoryQueries.update(e.category.name, e.category.color, e.category.id)
            is CategoryArchived -> db.categoryQueries.archive(e.categoryId)
            is TransactionAdded -> with(e.transaction) {
                e.receipt?.let { receipt ->
                    require(id == receipt.transactionId && source == rs.budget.domain.TransactionSource.RECEIPT)
                    if (db.receiptQueries.byId(receipt.id).executeAsOneOrNull() != null) return
                    db.receiptQueries.insert(receipt.id, id, receipt.issuer, receipt.invoiceNumber,
                        receipt.fiscalUrl, receipt.merchant, receipt.date.toString(),
                        receipt.total.amount.toPlainString(), EventJson.encodeToString(receipt.items))
                }
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
                db.recurringRuleQueries.insertIgnore(id, name, money.amount.toPlainString(), money.currency.name, accountId, categoryId, cadence.name, firstDue.toString())
                db.recurringRuleQueries.update(name, money.amount.toPlainString(), money.currency.name, accountId, categoryId, cadence.name, firstDue.toString(), id)
            }
            is RecurringRuleArchived -> db.recurringRuleQueries.archive(e.ruleId)
            is BillSkipped -> db.billSkipQueries.insertIgnore(e.ruleId, e.period)
            is GoalSet -> with(e.goal) {
                db.goalQueries.insertIgnore(id, name, target.amount.toPlainString(), monthly.amount.toPlainString(), target.currency.name)
                db.goalQueries.update(name, target.amount.toPlainString(), monthly.amount.toPlainString(), target.currency.name, id)
            }
            is GoalArchived -> db.goalQueries.archive(e.goalId)
            is GoalAllocated -> with(e.allocation) {
                db.goalAllocationQueries.insertIgnore(id, goalId, money.amount.toPlainString(), money.currency.name, date.toString())
            }
            is GoalAllocationDeleted -> db.goalAllocationQueries.delete(e.allocationId)
        }
    }

    fun clear() {
        db.exchangeRateOverrideQueries.deleteAll()
        db.receiptQueries.deleteAll()
        db.goalAllocationQueries.deleteAll()
        db.goalQueries.deleteAll()
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
        const val VERSION = 5
    }
}
