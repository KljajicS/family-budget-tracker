package rs.budget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.budget.data.db.BudgetDb
import rs.budget.data.db.Event as EventRow
import rs.budget.domain.Event
import rs.budget.domain.EventJson
import rs.budget.domain.EventRecord
import rs.budget.domain.newId
import java.time.Instant

class EventStore(
    private val db: BudgetDb,
    private val deviceId: String,
    private val clock: Clock,
    private val projector: Projector,
) {
    suspend fun append(event: Event): EventRecord = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            val seq = (db.eventQueries.lastSeq(deviceId).executeAsOne().max_seq ?: 0L) + 1
            val record = EventRecord(newId(), deviceId, seq, clock.now(), event)
            insert(record)
            projector.apply(record)
            record
        }
    }

    /** Records from other devices. Unknown `(deviceId, seq)` pairs are stored and projected; the rest are skipped. */
    fun applyRemote(records: List<EventRecord>) = db.transaction {
        records
            .filter { db.eventQueries.countFor(it.deviceId, it.seq).executeAsOne() == 0L }
            .sortedWith(compareBy({ it.occurredAt }, { it.deviceId }, { it.seq }))
            .forEach {
                insert(it)
                projector.apply(it)
            }
    }

    fun rebuild() = db.transaction {
        projector.clear()
        db.eventQueries.allInOrder().executeAsList().forEach { projector.apply(toRecord(it)) }
    }

    private fun insert(r: EventRecord) = db.eventQueries.insert(
        id = r.id,
        device_id = r.deviceId,
        seq = r.seq,
        occurred_at = r.occurredAt.toEpochMilli(),
        type = r.event::class.simpleName!!,
        payload = EventJson.encodeToString(Event.serializer(), r.event),
    )

    private fun toRecord(row: EventRow) = EventRecord(
        id = row.id,
        deviceId = row.device_id,
        seq = row.seq,
        occurredAt = Instant.ofEpochMilli(row.occurred_at),
        event = EventJson.decodeFromString(Event.serializer(), row.payload),
    )
}
