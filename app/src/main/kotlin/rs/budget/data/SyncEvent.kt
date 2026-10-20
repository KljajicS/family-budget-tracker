package rs.budget.data

import kotlinx.serialization.Serializable
import rs.budget.domain.Event
import rs.budget.domain.EventJson
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionSource
import java.time.DateTimeException

/** One log row exactly as stored; payload text is never re-encoded, so fields and types this version cannot read survive. */
@Serializable
data class RawEventRecord(
    val id: String,
    val deviceId: String,
    val seq: Long,
    val occurredAt: Long,
    val type: String,
    val payload: String,
)

enum class SyncIssueKind {
    /** The log holds an event this version cannot read; its effect is missing, so financial writes should wait for an update. */
    INCOMPATIBLE_EVENT,
    /** A stored event breaks an invariant (receipt identity) and was skipped. */
    INVALID_EVENT,
    /** A second add for an existing transaction id carried different money, account or date; the first one is kept. */
    TRANSACTION_CONFLICT,
    /** A loan, loan payment, adjustment or receipt link collided with a different existing one and was not applied. */
    LOAN_CONFLICT,
    /** A currency change reached an account that already had transactions or bills; the old currency is kept. */
    ACCOUNT_CURRENCY_CONFLICT,
    /** A transaction's currency differs from its account's, e.g. after a currency change merged with an older-currency entry. */
    CURRENCY_MISMATCH,
}

/** [eventId] is null for issues derived from current state rather than a single event. */
data class SyncIssue(val kind: SyncIssueKind, val subjectId: String?, val eventId: String?)

class SyncImportException(val reason: Reason, val index: Int? = null) : Exception("Sync import rejected: $reason") {
    enum class Reason {
        /** Out-of-bounds envelope, malformed payload, or a payload whose type disagrees with the envelope. */
        INVALID_RECORD,
        /** Same event id or (device, seq) with different content, in the batch or against the log. */
        IDENTITY_CONFLICT,
        /** The local log changed after the user confirmed the replacement. */
        STALE_REPLACEMENT,
    }
}

/** Null when this version cannot read the payload (newer type or shape); never thrown for those. */
internal fun decodeEventOrNull(payload: String): Event? = try {
    EventJson.decodeFromString(Event.serializer(), payload)
} catch (_: IllegalArgumentException) {
    null
} catch (_: DateTimeException) {
    null
}

internal fun Event.hasConsistentReceipt(): Boolean = this !is TransactionAdded || receipt == null ||
    (transaction.id == receipt.transactionId && transaction.source == TransactionSource.RECEIPT)
