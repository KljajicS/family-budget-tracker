package app.kinjar.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.kinjar.data.EventStore
import app.kinjar.data.RawEventRecord
import app.kinjar.data.SyncImportException
import app.kinjar.data.SyncIssueKind

@Serializable
private data class HistorySnapshot(val version: Int = 1, val records: List<RawEventRecord>)

class EventSyncHistory(private val store: EventStore) : SyncHistory {
    private val json = Json { encodeDefaults = true }

    override suspend fun exportSnapshot(): ByteArray = withContext(Dispatchers.IO) {
        json.encodeToString(HistorySnapshot(records = store.exportRaw())).encodeToByteArray()
    }

    override suspend fun mergeSnapshot(bytes: ByteArray): MergeOutcome = withContext(Dispatchers.IO) {
        val records = decode(bytes)
        try {
            store.importRaw(records)
        } catch (_: SyncImportException) {
            throw InvalidSyncFile()
        }
        status()
    }

    override suspend fun status(): MergeOutcome = withContext(Dispatchers.IO) {
        val issues = store.syncIssues()
        MergeOutcome(
            requiresUpgrade = issues.any { it.kind == SyncIssueKind.INCOMPATIBLE_EVENT },
            conflictCount = issues.count { it.kind != SyncIssueKind.INCOMPATIBLE_EVENT },
        )
    }

    fun decode(bytes: ByteArray): List<RawEventRecord> {
        if (bytes.size > MAX_BYTES) throw InvalidSyncFile()
        val snapshot = try {
            json.decodeFromString<HistorySnapshot>(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (_: SerializationException) {
            throw InvalidSyncFile()
        } catch (_: CharacterCodingException) {
            throw InvalidSyncFile()
        }
        if (snapshot.version != 1 || snapshot.records.size > EventStore.MAX_RECORDS) throw InvalidSyncFile()
        return snapshot.records
    }

    companion object { const val MAX_BYTES = 16 * 1024 * 1024 }
}
