package rs.budget.data

import rs.budget.data.db.BudgetDb
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import rs.budget.domain.LoanPayment
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.EventJson
import rs.budget.domain.AccountArchived
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountUpdated
import rs.budget.domain.AccountsReordered
import rs.budget.domain.BillSkipped
import rs.budget.domain.GoalsReordered
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
import rs.budget.domain.LoanCreated
import rs.budget.domain.LoanUpdated
import rs.budget.domain.LoanArchived
import rs.budget.domain.LoanPaymentAdded
import rs.budget.domain.LoanPaymentUpdated
import rs.budget.domain.LoanPaymentRemoved
import rs.budget.domain.LoanPrincipalAdjusted
import rs.budget.domain.LoanReceiptLinked

// Replay in log order so older edits cannot overwrite newer changes.
class Projector(private val db: BudgetDb) {

    fun apply(record: EventRecord) {
        val at = record.occurredAt.toEpochMilli()
        when (val e = record.event) {
            is LoanCreated -> {
                val payload = EventJson.encodeToString(e.loan)
                if (db.loanQueries.insertIgnore(e.loan.id, payload).value == 0L &&
                    db.loanQueries.byId(e.loan.id).executeAsOne().payload != payload) issue(record, SyncIssueKind.LOAN_CONFLICT, e.loan.id)
            }
            is LoanUpdated -> db.loanQueries.update(EventJson.encodeToString(e.loan), e.loan.id)
            is LoanArchived -> db.loanQueries.archive(e.loanId)
            is LoanPaymentAdded -> addPayment(record, e.payment)
            is LoanPaymentUpdated -> db.loanPaymentQueries.update(EventJson.encodeToString(e.payment), e.payment.id)
            is LoanPaymentRemoved -> {
                db.loanPaymentQueries.insertTombstone(e.paymentId)
                db.loanPaymentQueries.remove(e.paymentId)
            }
            is LoanPrincipalAdjusted -> with(e.adjustment) {
                val payload = EventJson.encodeToString(this)
                if (db.loanAdjustmentQueries.insertIgnore(id, loanId, payload).value == 0L &&
                    db.loanAdjustmentQueries.byId(id).executeAsOne().payload != payload) issue(record, SyncIssueKind.LOAN_CONFLICT, loanId)
            }
            is LoanReceiptLinked -> if (db.loanReceiptQueries.insertIgnore(e.id, e.loanId, e.transactionId).value == 0L) {
                val link = db.loanReceiptQueries.forTransaction(e.transactionId).executeAsOneOrNull()
                if (link?.id != e.id || link.loan_id != e.loanId) issue(record, SyncIssueKind.LOAN_CONFLICT, e.loanId)
            }
            is ExchangeRateOverrideSet -> db.exchangeRateOverrideQueries.upsert(e.currency.name, e.rate, at)
            is MemberCreated -> db.memberQueries.insertIgnore(e.member.id, e.member.displayName, e.member.deviceId)
            is MemberRenamed -> db.memberQueries.rename(e.displayName, e.memberId)
            is AccountCreated -> db.accountQueries.insertIgnore(
                e.account.id, e.account.name, e.account.currency.name, e.account.kind.name, if (e.account.archived) 1 else 0,
            )
            // Edits never change archived: a stale edit from the other phone must not bring a deleted row back (ADR 8).
            is AccountUpdated -> with(e.account) {
                val previous = db.accountQueries.byId(id).executeAsOneOrNull()?.currency
                val keep = previous != null && previous != currency.name && db.accountQueries.inUse(id).executeAsOne() > 0
                db.accountQueries.update(name, if (keep) previous else currency.name, kind.name, id)
                if (keep) issue(record, SyncIssueKind.ACCOUNT_CURRENCY_CONFLICT, id) else clear(SyncIssueKind.ACCOUNT_CURRENCY_CONFLICT, id)
            }
            is AccountArchived -> db.accountQueries.archive(e.accountId)
            is AccountsReordered -> {
                val existing = db.accountQueries.all().executeAsList().map { it.id }
                (e.accountIds.filter { it in existing } + existing).distinct()
                    .forEachIndexed { i, id -> db.accountQueries.setPosition(i.toLong(), id) }
            }
            is CategoryCreated -> db.categoryQueries.insertIgnore(
                e.category.id, e.category.name, e.category.color, if (e.category.archived) 1 else 0,
            )
            is CategoryUpdated -> db.categoryQueries.update(e.category.name, e.category.color, e.category.id)
            is CategoryArchived -> db.categoryQueries.archive(e.categoryId)
            is TransactionAdded -> addTransaction(record, e)
            is TransactionEdited -> with(e.transaction) {
                db.txQueries.update(
                    type.name, money.amount.toPlainString(), money.currency.name, accountId, toAccountId, categoryId,
                    date.toString(), note, merchant, paidBy, source.name, at, id,
                )
                clear(SyncIssueKind.TRANSACTION_CONFLICT, id)
            }
            is TransactionDeleted -> {
                db.txTombstoneQueries.insert(e.transactionId)
                db.txQueries.delete(e.transactionId)
                clear(SyncIssueKind.TRANSACTION_CONFLICT, e.transactionId)
            }
            is BudgetSet -> db.budgetQueries.upsert(
                e.budget.categoryId, e.budget.month.toString(), e.budget.money.amount.toPlainString(), e.budget.money.currency.name,
            )
            // Legacy budget events still replay so old logs rebuild unchanged; a zero row was the removal tombstone.
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
            is GoalsReordered -> {
                val existing = db.goalQueries.all().executeAsList().map { it.id }
                (e.goalIds.filter { it in existing } + existing).distinct()
                    .forEachIndexed { i, id -> db.goalQueries.setPosition(i.toLong(), id) }
            }
            is GoalAllocated -> with(e.allocation) {
                db.goalAllocationQueries.insertIgnore(id, goalId, money.amount.toPlainString(), money.currency.name, date.toString())
            }
            is GoalAllocationDeleted -> db.goalAllocationQueries.delete(e.allocationId)
        }
    }

    fun reportIncompatible(eventId: String) {
        db.syncIssueQueries.insert(eventId, SyncIssueKind.INCOMPATIBLE_EVENT.name, null)
    }

    private fun addTransaction(record: EventRecord, e: TransactionAdded): Unit = with(e.transaction) {
        val receipt = e.receipt
        if (!e.hasConsistentReceipt()) return issue(record, SyncIssueKind.INVALID_EVENT, id)
        // Delete wins over any later add of the same id, except a bill payment: deleting one reopens the bill,
        // and paying it again legitimately reuses the period's id.
        if (source != TransactionSource.RECURRING && db.txTombstoneQueries.contains(id).executeAsOne() > 0) return
        if (receipt != null) {
            val existingReceipt = db.receiptQueries.byId(receipt.id).executeAsOneOrNull()
            if (existingReceipt != null) {
                if (existingReceipt.transaction_id != id) return issue(record, SyncIssueKind.TRANSACTION_CONFLICT, existingReceipt.transaction_id)
                return reportDuplicate(record, this)
            }
        }
        receipt?.let {
            db.receiptQueries.insert(it.id, id, it.issuer, it.invoiceNumber, it.fiscalUrl, it.merchant, it.date.toString(),
                it.total.amount.toPlainString(), EventJson.encodeToString(it.items))
        }
        val inserted = db.txQueries.insertIgnore(
            id, type.name, money.amount.toPlainString(), money.currency.name, accountId, toAccountId, categoryId,
            date.toString(), note, merchant, paidBy, source.name, record.occurredAt.toEpochMilli(),
        ).value
        if (inserted == 0L) reportDuplicate(record, this)
    }

    /** The first add is kept; a second one that moves different money is recorded instead of vanishing. */
    private fun reportDuplicate(record: EventRecord, t: Transaction) {
        val kept = db.txQueries.byId(t.id).executeAsOneOrNull() ?: return
        val same = kept.type == t.type.name && kept.amount == t.money.amount.toPlainString() && kept.currency == t.money.currency.name &&
            kept.account_id == t.accountId && kept.to_account_id == t.toAccountId && kept.date == t.date.toString()
        if (!same) issue(record, SyncIssueKind.TRANSACTION_CONFLICT, t.id)
    }

    /** A removed payment id stays removed; the same payment delivered twice is harmless; anything else is a visible conflict. */
    private fun addPayment(record: EventRecord, p: LoanPayment) {
        if (db.loanPaymentQueries.insertIgnore(p.id, p.loanId, p.transactionId, EventJson.encodeToString(p)).value != 0L) {
            return clear(SyncIssueKind.LOAN_CONFLICT, p.transactionId)
        }
        val byId = db.loanPaymentQueries.byId(p.id).executeAsOneOrNull()
        if (byId != null && byId.removed != 0L) return
        val existing = (byId ?: db.loanPaymentQueries.forTransaction(p.transactionId).executeAsOneOrNull())
            ?.payload?.let { EventJson.decodeFromString<LoanPayment>(it) }
        if (existing == null || existing.transactionId != p.transactionId || existing.loanId != p.loanId || existing.total != p.total) {
            issue(record, SyncIssueKind.LOAN_CONFLICT, p.transactionId)
        }
    }

    private fun issue(record: EventRecord, kind: SyncIssueKind, subjectId: String) {
        db.syncIssueQueries.insert(record.id, kind.name, subjectId)
    }

    private fun clear(kind: SyncIssueKind, subjectId: String) {
        db.syncIssueQueries.clearSubject(kind.name, subjectId)
    }

    fun clear() {
        db.syncIssueQueries.deleteAll()
        db.txTombstoneQueries.deleteAll()
        db.loanPaymentQueries.deleteAll()
        db.loanReceiptQueries.deleteAll()
        db.loanAdjustmentQueries.deleteAll()
        db.loanQueries.deleteAll()
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
        const val VERSION = 7
    }
}
