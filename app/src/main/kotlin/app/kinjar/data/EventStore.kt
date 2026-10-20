package app.kinjar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.kinjar.data.SyncImportException.Reason
import app.kinjar.data.db.KinjarDb
import app.kinjar.data.db.Event as EventRow
import app.kinjar.domain.Event
import app.kinjar.domain.EventJson
import app.kinjar.domain.EventRecord
import app.kinjar.domain.newId
import java.time.Instant

/**
 * The single writer of the event log. Every write, raw import, export and replacement takes the same lock,
 * so a sync engine must share this instance rather than create another.
 */
class EventStore(
    private val db: KinjarDb,
    private val deviceId: String,
    private val clock: Clock,
    private val projector: Projector,
) {
    private val writeLock = Any()

    fun <T> readConsistently(block: () -> T): T = synchronized(writeLock) {
        db.transactionWithResult { block() }
    }

    suspend fun append(event: Event, validate: () -> Unit = {}): EventRecord = appendAll(listOf(event), validate = validate).single()

    suspend fun appendAll(events: List<Event>, skipIf: () -> Boolean = { false }, validate: () -> Unit = {}): List<EventRecord> = withContext(Dispatchers.IO) {
        synchronized(writeLock) {
            db.transactionWithResult {
                if (skipIf()) return@transactionWithResult emptyList()
                check(db.syncIssueQueries.incompatibleCount().executeAsOne() == 0L) {
                    "Update required before changing family data"
                }
                validate()
                require(events.all { it.hasConsistentReceipt() }) { "Receipt does not match its transaction" }
                events.map { event ->
                    val seq = (db.eventQueries.lastSeq(deviceId).executeAsOne().max_seq ?: 0L) + 1
                    // Logical clock preserves causality when a device clock moves backwards.
                    val last = db.eventQueries.lastOccurredAt().executeAsOne().max_at ?: 0L
                    val at = Instant.ofEpochMilli(maxOf(clock.now().toEpochMilli(), last + 1))
                    val record = EventRecord(newId(), deviceId, seq, at, event)
                    insert(record.toRaw())
                    projector.apply(record)
                    record
                }
            }
        }
    }

    /** Typed adapter over [importRaw]; payloads are encoded by this version. */
    fun applyRemote(records: List<EventRecord>) { importRaw(records.map { it.toRaw() }) }

    /** Whole log in replay order, payloads byte-for-byte as stored. */
    fun exportRaw(): List<RawEventRecord> = synchronized(writeLock) { db.transactionWithResult { allRaw() } }

    /**
     * Adds records not yet in the log and rebuilds the projection, all or nothing: any invalid record or identity
     * conflict rejects the whole batch with [SyncImportException]. Exact duplicates are skipped. Unreadable types
     * are stored unchanged and surface as [SyncIssueKind.INCOMPATIBLE_EVENT]. Returns how many records were new.
     */
    fun importRaw(records: List<RawEventRecord>): Int = synchronized(writeLock) {
        db.transactionWithResult {
            val unique = validated(records)
            val fresh = unique.filter { record ->
                val stored = listOfNotNull(db.eventQueries.byId(record.id).executeAsOneOrNull(),
                    db.eventQueries.bySeq(record.deviceId, record.seq).executeAsOneOrNull())
                if (stored.any { it.toRaw() != record }) throw SyncImportException(Reason.IDENTITY_CONFLICT, records.indexOf(record))
                stored.isEmpty()
            }
            if (fresh.isNotEmpty()) {
                fresh.forEach(::insert)
                rebuildInTransaction()
            }
            fresh.size
        }
    }

    /**
     * Swaps the whole log for [records] (joining a family replaces this phone's data). [confirmedLocal] is the
     * [exportRaw] the user saw when confirming; if the log changed since, nothing is replaced.
     */
    fun replaceRaw(records: List<RawEventRecord>, confirmedLocal: List<RawEventRecord>) = synchronized(writeLock) {
        db.transaction {
            val unique = validated(records)
            if (allRaw() != confirmedLocal) throw SyncImportException(Reason.STALE_REPLACEMENT)
            db.eventQueries.deleteAll()
            unique.forEach(::insert)
            rebuildInTransaction()
        }
    }

    fun syncIssues(): List<SyncIssue> = synchronized(writeLock) {
        db.transactionWithResult {
            db.syncIssueQueries.all().executeAsList().map { SyncIssue(SyncIssueKind.valueOf(it.kind), it.subject_id, it.event_id) } +
                db.syncIssueQueries.currencyMismatches().executeAsList().map { SyncIssue(SyncIssueKind.CURRENCY_MISMATCH, it, null) }
        }
    }

    fun rebuild() = synchronized(writeLock) { db.transaction { rebuildInTransaction() } }

    private fun rebuildInTransaction() {
        projector.clear()
        db.eventQueries.allInOrder().executeAsList().forEach { row ->
            val record = toRecord(row)
            if (record == null) projector.reportIncompatible(row.id) else projector.apply(record)
        }
    }

    /** Checks bounds and in-batch identity, returning the batch without exact duplicates. */
    private fun validated(records: List<RawEventRecord>): List<RawEventRecord> {
        if (records.size > MAX_RECORDS) throw SyncImportException(Reason.INVALID_RECORD)
        val latest = clock.now().toEpochMilli() + MAX_FUTURE_MILLIS
        val byId = HashMap<String, RawEventRecord>()
        val bySeq = HashMap<Pair<String, Long>, RawEventRecord>()
        records.forEachIndexed { i, r ->
            if (!isValid(r, latest)) throw SyncImportException(Reason.INVALID_RECORD, i)
            if (byId.getOrPut(r.id) { r } != r || bySeq.getOrPut(r.deviceId to r.seq) { r } != r) {
                throw SyncImportException(Reason.IDENTITY_CONFLICT, i)
            }
        }
        return records.distinct()
    }

    private fun isValid(r: RawEventRecord, latest: Long): Boolean {
        if (!isToken(r.id) || !isToken(r.deviceId) || !isToken(r.type)) return false
        if (r.seq <= 0 || r.occurredAt < 0 || r.occurredAt > latest || r.payload.length > MAX_PAYLOAD_LENGTH) return false
        val json = try {
            EventJson.parseToJsonElement(r.payload) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        } ?: return false
        val type = json["type"] as? JsonPrimitive
        if (type?.isString != true || type.content != r.type) return false
        return decodeEventOrNull(r.payload)?.hasConsistentReceipt() != false
    }

    private fun isToken(value: String) = value.length in 1..MAX_TOKEN_LENGTH && value.all { it in '!'..'~' }

    private fun allRaw() = db.eventQueries.allInOrder().executeAsList().map { it.toRaw() }

    private fun insert(r: RawEventRecord) {
        db.eventQueries.insert(r.id, r.deviceId, r.seq, r.occurredAt, r.type, r.payload)
    }

    private fun EventRecord.toRaw(): RawEventRecord {
        val payload = EventJson.encodeToString(Event.serializer(), event)
        val type = EventJson.parseToJsonElement(payload).jsonObject.getValue("type").jsonPrimitive.content
        return RawEventRecord(id, deviceId, seq, occurredAt.toEpochMilli(), type, payload)
    }

    private fun EventRow.toRaw() = RawEventRecord(id, device_id, seq, occurred_at, type, payload)

    /** Null for events this app version cannot read (newer type or shape); they stay in the log and are skipped. */
    private fun toRecord(row: EventRow): EventRecord? =
        decodeEventOrNull(row.payload)?.let { EventRecord(row.id, row.device_id, row.seq, Instant.ofEpochMilli(row.occurred_at), it) }

    companion object {
        /** Clock skew tolerated from other phones; their later timestamps also push this phone's logical clock. */
        const val MAX_FUTURE_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_TOKEN_LENGTH = 128
        const val MAX_PAYLOAD_LENGTH = 512 * 1024
        const val MAX_RECORDS = 100_000
    }
}
