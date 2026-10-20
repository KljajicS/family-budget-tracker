package app.kinjar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.kinjar.data.db.KinjarDb
import app.kinjar.data.db.Event as EventRow
import app.kinjar.domain.Event
import app.kinjar.domain.EventJson
import app.kinjar.domain.EventRecord
import app.kinjar.domain.newId
import java.time.Instant

class EventStore(
    private val db: KinjarDb,
    private val deviceId: String,
    private val clock: Clock,
    private val projector: Projector,
) {
    private val writeLock = Any()

    suspend fun append(event: Event, validate: () -> Unit = {}): EventRecord = appendAll(listOf(event), validate = validate).single()

    suspend fun appendAll(events: List<Event>, skipIf: () -> Boolean = { false }, validate: () -> Unit = {}): List<EventRecord> = withContext(Dispatchers.IO) {
        synchronized(writeLock) {
            db.transactionWithResult {
                if (skipIf()) return@transactionWithResult emptyList()
                validate()
                events.map { event ->
                    val seq = (db.eventQueries.lastSeq(deviceId).executeAsOne().max_seq ?: 0L) + 1
                    // Logical clock preserves causality when a device clock moves backwards.
                    val last = db.eventQueries.lastOccurredAt().executeAsOne().max_at ?: 0L
                    val at = Instant.ofEpochMilli(maxOf(clock.now().toEpochMilli(), last + 1))
                    val record = EventRecord(newId(), deviceId, seq, at, event)
                    insert(record)
                    projector.apply(record)
                    record
                }
            }
        }
    }

    fun applyRemote(records: List<EventRecord>) = synchronized(writeLock) { db.transaction {
        val unique = records.groupBy { it.deviceId to it.seq }.values.map { duplicates ->
            require(duplicates.all { it == duplicates.first() }) { "Conflicting event sequence" }
            duplicates.first()
        }
        val fresh = unique.filter { db.eventQueries.countFor(it.deviceId, it.seq).executeAsOne() == 0L }
        if (fresh.isEmpty()) return@transaction
        fresh.forEach(::insert)
        rebuildInTransaction()
    } }

    fun rebuild() = synchronized(writeLock) { db.transaction { rebuildInTransaction() } }

    private fun rebuildInTransaction() {
        projector.clear()
        db.eventQueries.allInOrder().executeAsList().forEach { row -> toRecord(row)?.let(projector::apply) }
    }

    private fun insert(r: EventRecord) {
        val payload = EventJson.encodeToString(Event.serializer(), r.event)
        db.eventQueries.insert(
            id = r.id,
            device_id = r.deviceId,
            seq = r.seq,
            occurred_at = r.occurredAt.toEpochMilli(),
            type = EventJson.parseToJsonElement(payload).jsonObject.getValue("type").jsonPrimitive.content,
            payload = payload,
        )
    }

    /** Null for events this app version cannot read (newer type or shape); they stay in the log and are skipped. */
    private fun toRecord(row: EventRow): EventRecord? {
        val event = runCatching { EventJson.decodeFromString(Event.serializer(), row.payload) }.getOrNull() ?: return null
        return EventRecord(row.id, row.device_id, row.seq, Instant.ofEpochMilli(row.occurred_at), event)
    }
}
