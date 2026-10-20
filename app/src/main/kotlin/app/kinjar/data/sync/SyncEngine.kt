package app.kinjar.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

data class SyncObject(val id: String)

interface SyncTransport {
    suspend fun listHeads(): List<SyncObject>
    suspend fun hasOwnHead(): Boolean
    suspend fun download(file: SyncObject): ByteArray
    suspend fun publishHead(bytes: ByteArray)
    suspend fun retainDailyHistory(bytes: ByteArray)
}

interface SyncHistory {
    suspend fun exportSnapshot(): ByteArray
    suspend fun mergeSnapshot(bytes: ByteArray): MergeOutcome
    suspend fun status(): MergeOutcome
}

data class MergeOutcome(val requiresUpgrade: Boolean = false, val conflictCount: Int = 0)

class OpenedSnapshot(val publisher: String, val generation: Long, val payload: ByteArray)

interface SyncEncryption {
    fun open(bytes: ByteArray): OpenedSnapshot
    fun seal(payload: ByteArray, generation: Long): ByteArray
}

data class ObservedSnapshot(val generation: Long, val digest: String)

interface SyncProgress {
    suspend fun observed(publisher: String): ObservedSnapshot?
    suspend fun remember(publisher: String, value: ObservedSnapshot)
    suspend fun uploadedDigest(): String?
    suspend fun reserveGeneration(): Long
    suspend fun markUploaded(digest: String)
}

enum class SyncProblem { INVALID_FILE, ROLLBACK, CONFLICTING_GENERATION }
data class SyncFileProblem(val fileId: String, val reason: SyncProblem)
data class SyncResult(
    val issues: List<SyncFileProblem>,
    val requiresUpgrade: Boolean,
    val conflictCount: Int,
    val uploaded: Boolean,
)

class InvalidSyncFile : Exception("Invalid sync file")

class SyncEngine(
    private val transport: SyncTransport,
    private val history: SyncHistory,
    private val encryption: SyncEncryption,
    private val progress: SyncProgress,
    private val maxFileBytes: Int = 16 * 1024 * 1024,
    private val maxHeads: Int = 100,
) {
    private val mutex = Mutex()

    suspend fun synchronize(): SyncResult = mutex.withLock {
        val files = transport.listHeads()
        if (files.size > maxHeads) throw InvalidSyncFile()
        val issues = mutableListOf<SyncFileProblem>()
        val initialStatus = history.status()
        var requiresUpgrade = initialStatus.requiresUpgrade
        var conflictCount = initialStatus.conflictCount
        for (file in files.distinctBy { it.id }) {
            // Transport failures abort the run; only rejected content is isolated per file.
            val bytes = transport.download(file)
            try {
                checkSize(bytes)
                val opened = encryption.open(bytes)
                checkSize(opened.payload)
                if (opened.generation <= 0 || opened.publisher.isBlank()) throw InvalidSyncFile()
                val digest = digest(opened.payload)
                val previous = progress.observed(opened.publisher)
                val problem = when {
                    previous != null && opened.generation < previous.generation -> SyncProblem.ROLLBACK
                    previous != null && opened.generation == previous.generation && digest != previous.digest -> SyncProblem.CONFLICTING_GENERATION
                    else -> null
                }
                if (problem != null) {
                    issues += SyncFileProblem(file.id, problem)
                    continue
                }
                val merged = history.mergeSnapshot(opened.payload)
                requiresUpgrade = requiresUpgrade || merged.requiresUpgrade
                conflictCount = maxOf(conflictCount, merged.conflictCount)
                progress.remember(opened.publisher, ObservedSnapshot(opened.generation, digest))
            } catch (_: InvalidSyncFile) {
                issues += SyncFileProblem(file.id, SyncProblem.INVALID_FILE)
            }
        }
        val snapshot = history.exportSnapshot()
        checkSize(snapshot)
        val digest = digest(snapshot)
        val changed = digest != progress.uploadedDigest() || !transport.hasOwnHead()
        // A rejected remote file may contain unseen history. Do not overwrite recovery copies.
        if (issues.isNotEmpty() || requiresUpgrade) {
            return@withLock SyncResult(issues, requiresUpgrade, conflictCount, uploaded = false)
        }
        val sealed = encryption.seal(snapshot, progress.reserveGeneration())
        checkSize(sealed)
        transport.retainDailyHistory(sealed)
        if (changed) {
            transport.publishHead(sealed)
            progress.markUploaded(digest)
        }
        SyncResult(issues, requiresUpgrade, conflictCount, uploaded = changed)
    }

    private fun checkSize(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > maxFileBytes) throw InvalidSyncFile()
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
