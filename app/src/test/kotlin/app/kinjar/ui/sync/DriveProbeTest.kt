package app.kinjar.ui.sync

import app.kinjar.data.sync.drive.DriveFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DriveProbeTest {
    private val probe = DriveFile(
        id = "peer_1", name = "${PROBE_NAME_PREFIX}AB12.txt", mimeType = PROBE_MIME, size = 120, modifiedTime = null,
        md5Checksum = "abc", version = 4, ownedByMe = false, appProperties = mapOf(MARKER_KEY to MARKER_VALUE), trashed = false,
    )

    @Test fun `only the exact requested synthetic probe is accepted`() {
        assertSame(probe, checkProbe(probe, "peer_1", owned = false))
        val rejected = listOf(
            "UNRELATED_FILE_ID" to probe.copy(id = "other"),
            "TRASHED_OR_UNKNOWN" to probe.copy(trashed = true),
            "TRASHED_OR_UNKNOWN" to probe.copy(trashed = null),
            "MARKER_MISSING" to probe.copy(appProperties = emptyMap()),
            "MARKER_MISSING" to probe.copy(appProperties = mapOf(MARKER_KEY to "v2")),
            "NOT_OTHER_PHONE_FILE" to probe.copy(ownedByMe = true),
            "NOT_OTHER_PHONE_FILE" to probe.copy(ownedByMe = null),
            "WRONG_MIME" to probe.copy(mimeType = "application/vnd.google-apps.folder"),
            "WRONG_SIZE" to probe.copy(size = MAX_PROBE_BYTES + 1),
            "WRONG_SIZE" to probe.copy(size = null),
            "WRONG_NAME" to probe.copy(name = "Budget.txt"),
        )
        rejected.forEach { (code, file) -> assertEquals(code, rejection { checkProbe(file, "peer_1", owned = false) }) }
        assertEquals("NOT_MY_FILE", rejection { checkProbe(probe, "peer_1", owned = true) })
    }

    @Test fun `content needs the versioned marker and size bound`() {
        assertEquals("$CONTENT_MARKER\nwrite: 1\n", checkProbeContent("$CONTENT_MARKER\nwrite: 1\n".encodeToByteArray()))
        for (text in listOf("budget data", "kinjar-sync-probe v2\n", "$CONTENT_MARKER\n" + "x".repeat(MAX_PROBE_BYTES.toInt()))) {
            assertEquals("CONTENT_MARKER_MISSING", rejection { checkProbeContent(text.encodeToByteArray()) })
        }
    }

    @Test fun `mailbox ignores unrelated and malformed keys`() {
        val requests = mailboxRequests(mapOf(
            MARKER_KEY to MARKER_VALUE,
            requestKey("peer_1") to "v1.0123abcd.1760000000",
            requestKey("peer_2") to "anything",
            requestKey("../x") to "v1.0123abcd.1",
            "otherApp" to "v1.0123abcd.1",
        ))
        assertEquals(mapOf("peer_1" to "v1.0123abcd.1760000000"), requests)
    }

    @Test fun `probe rejection shows only its code`() {
        assertEquals("PROBE_WRONG_MIME", driveTestError(ProbeRejected("WRONG_MIME")))
    }

    private fun rejection(block: () -> Unit): String? = (runCatching(block).exceptionOrNull() as? ProbeRejected)?.code
}
