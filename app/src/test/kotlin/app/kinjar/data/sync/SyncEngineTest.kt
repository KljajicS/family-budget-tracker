package app.kinjar.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncEngineTest {
    private class Fixture {
        val remote = linkedMapOf<String, ByteArray>()
        val observations = mutableMapOf<String, ObservedSnapshot>()
        var local = "local".toByteArray()
        var lastUpload: String? = null
        var generation = 0L
        var published = 0
        var retained = 0
        var failPublish = false
        var cancelDownload = false
        var upgrade = false
        var ownHeadPresent = true
        val merged = mutableListOf<String>()
        val engine = SyncEngine(
            object : SyncTransport {
                override suspend fun listHeads() = remote.keys.map(::SyncObject)
                override suspend fun hasOwnHead() = ownHeadPresent
                override suspend fun download(file: SyncObject): ByteArray {
                    if (cancelDownload) throw CancellationException()
                    return remote.getValue(file.id)
                }
                override suspend fun publishHead(bytes: ByteArray) {
                    if (failPublish) throw java.io.IOException("offline")
                    published++
                }
                override suspend fun retainDailyHistory(bytes: ByteArray) { retained++ }
            },
            object : SyncHistory {
                override suspend fun exportSnapshot() = local
                override suspend fun status() = MergeOutcome(requiresUpgrade = upgrade)
                override suspend fun mergeSnapshot(bytes: ByteArray): MergeOutcome {
                    merged += bytes.decodeToString()
                    return MergeOutcome(requiresUpgrade = upgrade)
                }
            },
            object : SyncEncryption {
                override fun open(bytes: ByteArray): OpenedSnapshot {
                    val fields = bytes.decodeToString().split(':', limit = 3)
                    if (fields.size != 3) throw InvalidSyncFile()
                    return OpenedSnapshot(fields[0], fields[1].toLong(), fields[2].toByteArray())
                }
                override fun seal(payload: ByteArray, generation: Long) = payload
            },
            object : SyncProgress {
                override suspend fun observed(publisher: String) = observations[publisher]
                override suspend fun remember(publisher: String, value: ObservedSnapshot) { observations[publisher] = value }
                override suspend fun uploadedDigest() = lastUpload
                override suspend fun reserveGeneration() = ++generation
                override suspend fun markUploaded(digest: String) { lastUpload = digest }
            },
        )
    }

    @Test fun `failed upload remains pending and next run retries`() = runTest {
        val f = Fixture()
        f.failPublish = true
        try { f.engine.synchronize(); fail() } catch (_: java.io.IOException) { }
        assertNull(f.lastUpload)
        f.failPublish = false
        assertTrue(f.engine.synchronize().uploaded)
        assertFalse(f.engine.synchronize().uploaded)
        assertEquals(1, f.published)
    }

    @Test fun `corrupt file does not block valid imports or overwrite recovery`() = runTest {
        val f = Fixture()
        f.remote["bad"] = "corrupt".toByteArray()
        f.remote["good"] = "peer:1:history".toByteArray()
        val result = f.engine.synchronize()
        assertEquals(listOf("history"), f.merged)
        assertEquals(SyncProblem.INVALID_FILE, result.issues.single().reason)
        assertEquals(0, f.published)
        assertEquals(0, f.retained)
    }

    @Test fun `old and equivocating generations cannot replace observed history`() = runTest {
        val f = Fixture()
        f.remote["file"] = "peer:2:first".toByteArray()
        f.engine.synchronize()
        f.remote["file"] = "peer:1:old".toByteArray()
        assertEquals(SyncProblem.ROLLBACK, f.engine.synchronize().issues.single().reason)
        f.remote["file"] = "peer:2:other".toByteArray()
        assertEquals(SyncProblem.CONFLICTING_GENERATION, f.engine.synchronize().issues.single().reason)
        assertEquals(listOf("first"), f.merged)
    }

    @Test fun `unknown required semantics do not publish a misleading complete snapshot`() = runTest {
        val f = Fixture()
        f.upgrade = true
        f.remote["newer"] = "peer:1:new-event".toByteArray()
        assertTrue(f.engine.synchronize().requiresUpgrade)
        assertEquals(0, f.published)
    }

    @Test fun `cancellation propagates without marking upload complete`() = runTest {
        val f = Fixture()
        f.remote["file"] = "peer:1:data".toByteArray()
        f.cancelDownload = true
        try { f.engine.synchronize(); fail() } catch (_: CancellationException) { }
        assertNull(f.lastUpload)
        assertEquals(0, f.retained)
    }

    @Test fun `missing own cloud file is repaired even when content has not changed`() = runTest {
        val f = Fixture()
        f.engine.synchronize()
        f.ownHeadPresent = false
        assertTrue(f.engine.synchronize().uploaded)
        assertEquals(2, f.published)
    }

    @Test fun `unsupported local history remains blocked when remote files disappear`() = runTest {
        val f = Fixture()
        f.upgrade = true
        assertTrue(f.engine.synchronize().requiresUpgrade)
        assertEquals(0, f.published)
    }
}
