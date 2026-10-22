package app.kinjar.ui.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.kinjar.data.sync.drive.DriveAuthorizer
import app.kinjar.data.sync.drive.DriveException
import app.kinjar.data.sync.drive.DriveFile
import app.kinjar.data.sync.drive.DriveTransport
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class DriveTestState(
    val busy: Boolean = false,
    val connected: Boolean = false,
    val accountName: String? = null,
    val ownFileId: String? = null,
    val peerFileId: String? = null,
    val report: List<String> = emptyList(),
    val error: String? = null,
    val resolution: IntentSenderRequest? = null,
)

/**
 * Synthetic one-file-per-phone probe. Each phone is the only content writer of its own file; the other phone
 * only writes one mailbox key into that file's appProperties.
 */
@HiltViewModel
class DriveTestViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("drive_test", Context.MODE_PRIVATE)
    private var account: Account? = prefs.getString("account", null)?.let { Account(it, "com.google") }
    private val tag = prefs.getString("tag", null) ?: UUID.randomUUID().toString().take(4).uppercase()
    private var pendingOwnId: String? = prefs.getString("pending_own", null)
    private var pendingPeerId: String? = prefs.getString("pending_peer", null)
    private var writes = prefs.getInt("writes", 0)
    private var peerVersion: Long? = prefs.getLong("peer_version", -1).takeIf { it >= 0 }
    private val mutable = MutableStateFlow(DriveTestState(
        connected = account != null && prefs.getBoolean("connected", true),
        accountName = account?.name,
        ownFileId = prefs.getString("own", null),
        peerFileId = prefs.getString("peer", null),
    ))
    val state = mutable.asStateFlow()
    private val auth = DriveAuthorizer(context) { account ?: throw DriveException.AuthRequired("Select an account") }
    private val drive = DriveTransport(auth)

    fun connect() = action {
        pendingPeerId = null
        save()
        acceptAccount(auth.authorize(auth.silentRequest(account)))
    }

    fun resolve(code: Int, data: Intent?) = action {
        val requested = pendingPeerId
        pendingPeerId = null
        save()
        val granted = auth.resultFromActivity(code, data)
        if (requested == null) acceptAccount(granted) else acceptPeer(requested, granted)
    }

    fun resolutionLaunched() { mutable.update { it.copy(resolution = null) } }

    fun createOwnFile() = action {
        state.value.ownFileId?.let { id ->
            report(ownLines(checkProbe(drive.metadata(id), id, owned = true)))
            return@action
        }
        val id = pendingOwnId ?: drive.generateFileId().also { pendingOwnId = it; save() }
        val file = try {
            drive.createFile(
                "$PROBE_NAME_PREFIX$tag.txt", null, probeContent(1), PROBE_MIME, id, mapOf(MARKER_KEY to MARKER_VALUE),
            )
        } catch (e: DriveException.Http) {
            // 409: an earlier attempt with this generated ID already created the file.
            if (e.code != 409) throw e
            drive.metadata(id)
        }
        checkProbe(file, id, owned = true)
        pendingOwnId = null
        writes = 1
        mutable.update { it.copy(ownFileId = id) }
        save()
        report(ownLines(file))
    }

    fun updateOwnFile() = action {
        val id = requireNotNull(state.value.ownFileId)
        checkProbe(drive.metadata(id), id, owned = true)
        writes++
        save()
        report(ownLines(checkProbe(drive.updateFile(id, probeContent(writes), PROBE_MIME), id, owned = true)))
    }

    fun share(email: String) = action {
        val id = requireNotNull(state.value.ownFileId)
        checkProbe(drive.metadata(id), id, owned = true)
        drive.shareWithEmail(id, email.trim())
        report(listOf("share: ok"))
    }

    fun grantPeer(raw: String) = action {
        val id = raw.trim().takeIf { runCatching { DriveTransport.checkId(it) }.isSuccess }
            ?: throw ProbeRejected("INVALID_FILE_ID")
        probeCheck(id != state.value.ownFileId && id != pendingOwnId, "OWN_FILE_ID")
        pendingPeerId = id
        save()
        val request = auth.pickerRequest(listOf(PROBE_MIME), allowFolderSelection = false, fileIds = listOf(id), account = account)
        when (val outcome = auth.authorize(request)) {
            is DriveAuthorizer.Outcome.NeedsResolution -> mutable.update { it.copy(resolution = outcome.request) }
            is DriveAuthorizer.Outcome.Granted -> {
                pendingPeerId = null
                save()
                acceptPeer(id, outcome)
            }
        }
    }

    fun readPeer() = action { report(readPeerFile(requireNotNull(state.value.peerFileId))) }

    fun sendRequest() = action {
        val own = requireNotNull(state.value.ownFileId)
        val peer = requireNotNull(state.value.peerFileId)
        val before = checkProbe(drive.metadata(peer), peer, owned = false)
        val key = requestKey(own)
        probeCheck(key in before.appProperties || before.appProperties.size < DriveTransport.MAX_APP_PROPERTIES, "MAILBOX_FULL")
        val value = "v1.${UUID.randomUUID().toString().take(8)}.${Instant.now().epochSecond}"
        val after = checkProbe(drive.updateAppProperties(peer, mapOf(key to value)), peer, owned = false)
        probeCheck(after.appProperties[key] == value, "REQUEST_NOT_STORED")
        probeCheck(sameContent(before, after), "CONTENT_CHANGED")
        report(listOf("request sent: $value", "other file version: ${after.version}", "other file content unchanged: yes"))
    }

    fun readMailbox() = action {
        val own = requireNotNull(state.value.ownFileId)
        val requests = mailboxRequests(checkProbe(drive.metadata(own), own, owned = true).appProperties)
        report(listOf("requests: ${requests.size}") + requests.map { (sender, value) ->
            "from ${if (sender == state.value.peerFileId) "other phone" else sender}: $value"
        })
    }

    fun removeRequest() = action {
        val own = requireNotNull(state.value.ownFileId)
        val peer = requireNotNull(state.value.peerFileId)
        val before = checkProbe(drive.metadata(own), own, owned = true)
        val key = requestKey(peer)
        val value = before.appProperties[key]
        probeCheck(value != null && REQUEST_VALUE.matches(value), "NO_MATCHING_REQUEST")
        val after = checkProbe(drive.updateAppProperties(own, mapOf(key to null)), own, owned = true)
        probeCheck(key !in after.appProperties, "REQUEST_NOT_REMOVED")
        probeCheck(sameContent(before, after), "CONTENT_CHANGED")
        report(listOf("removed: $value", "requests left: ${mailboxRequests(after.appProperties).size}", "my content unchanged: yes"))
    }

    fun revoke() = action {
        auth.revoke(requireNotNull(account))
        pendingPeerId = null
        mutable.update { it.copy(connected = false, resolution = null, report = emptyList()) }
        save()
    }

    private suspend fun acceptAccount(outcome: DriveAuthorizer.Outcome) {
        when (outcome) {
            is DriveAuthorizer.Outcome.NeedsResolution -> mutable.update { it.copy(resolution = outcome.request) }
            is DriveAuthorizer.Outcome.Granted -> {
                val selected = Account(drive.authorizedAccountEmail(outcome.accessToken), "com.google")
                if (account != selected) resetFiles()
                account = selected
                mutable.update { it.copy(connected = true, accountName = selected.name) }
                save()
            }
        }
    }

    private suspend fun acceptPeer(id: String, granted: DriveAuthorizer.Outcome.Granted) {
        DriveAuthorizer.requireExactPick(id, granted.pickedFileIds)
        val email = drive.authorizedAccountEmail(granted.accessToken)
        if (!email.equals(account?.name, ignoreCase = true)) {
            throw DriveException.AuthRequired("Grant came from another account", "ACCOUNT_MISMATCH")
        }
        peerVersion = null
        val lines = readPeerFile(id)
        mutable.update { it.copy(peerFileId = id) }
        save()
        report(lines)
    }

    private suspend fun readPeerFile(id: String): List<String> {
        val file = checkProbe(drive.metadata(id), id, owned = false)
        val text = checkProbeContent(drive.download(id, MAX_PROBE_BYTES))
        val previous = peerVersion
        peerVersion = file.version
        save()
        val own = state.value.ownFileId
        val mine = own?.let { file.appProperties[requestKey(it)] }
        return listOf(
            "other file: ${file.name}",
            "version: ${file.version ?: "?"} (previous read: ${previous ?: "-"})",
            "modified: ${file.modifiedTime ?: "?"}",
        ) + text.lines().drop(1).filter { it.isNotBlank() } + "my request on other file: ${mine ?: "none"}"
    }

    private fun ownLines(file: DriveFile) = listOf(
        "my file: ${file.name}",
        "version: ${file.version ?: "?"}, write: $writes",
        "requests: ${mailboxRequests(file.appProperties).size}",
    )

    private fun probeContent(write: Int) =
        "$CONTENT_MARKER\nphone: $tag\nwrite: $write\ntime: ${Instant.now()}\nSynthetic test data only.\n".encodeToByteArray()

    private fun resetFiles() {
        pendingOwnId = null
        pendingPeerId = null
        writes = 0
        peerVersion = null
        mutable.update { it.copy(ownFileId = null, peerFileId = null, report = emptyList()) }
    }

    private fun report(lines: List<String>) = mutable.update { it.copy(report = lines) }

    private suspend fun save() = withContext(Dispatchers.IO) {
        check(prefs.edit()
            .remove("folder").remove("probe").remove("probe_name")
            .putString("account", account?.name).putString("tag", tag)
            .putBoolean("connected", state.value.connected)
            .putString("own", state.value.ownFileId).putString("pending_own", pendingOwnId)
            .putString("peer", state.value.peerFileId).putString("pending_peer", pendingPeerId)
            .putInt("writes", writes).putLong("peer_version", peerVersion ?: -1)
            .commit())
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = driveTestError(e)) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}

internal const val PROBE_MIME = "text/plain"
internal const val PROBE_NAME_PREFIX = "Kinjar sync test "
internal const val MAX_PROBE_BYTES = 1024L
internal const val CONTENT_MARKER = "kinjar-sync-probe v1"
internal const val MARKER_KEY = "kjrProbe"
internal const val MARKER_VALUE = "v1"
private const val REQUEST_PREFIX = "kjrReq1."
private val REQUEST_VALUE = Regex("v1\\.[0-9a-f]{8}\\.[0-9]{1,12}")
private val FILE_ID = Regex("[A-Za-z0-9_-]{1,256}")

internal class ProbeRejected(val code: String) : Exception(code)

private fun probeCheck(ok: Boolean, code: String) {
    if (!ok) throw ProbeRejected(code)
}

/** Refuses any file that is not exactly the requested synthetic probe, before it is read or modified. */
internal fun checkProbe(file: DriveFile, id: String, owned: Boolean): DriveFile {
    probeCheck(file.id == id, "UNRELATED_FILE_ID")
    probeCheck(file.trashed == false, "TRASHED_OR_UNKNOWN")
    probeCheck(file.appProperties[MARKER_KEY] == MARKER_VALUE, "MARKER_MISSING")
    probeCheck(file.ownedByMe == owned, if (owned) "NOT_MY_FILE" else "NOT_OTHER_PHONE_FILE")
    probeCheck(file.mimeType == PROBE_MIME, "WRONG_MIME")
    probeCheck(file.size != null && file.size in 1..MAX_PROBE_BYTES, "WRONG_SIZE")
    probeCheck(file.name.startsWith(PROBE_NAME_PREFIX), "WRONG_NAME")
    return file
}

internal fun checkProbeContent(bytes: ByteArray): String {
    val text = bytes.decodeToString()
    probeCheck(bytes.size <= MAX_PROBE_BYTES && text.startsWith("$CONTENT_MARKER\n"), "CONTENT_MARKER_MISSING")
    return text
}

internal fun requestKey(senderFileId: String) = "$REQUEST_PREFIX$senderFileId"

/** Sender file ID to request value; foreign or malformed keys are ignored, never acted on. */
internal fun mailboxRequests(properties: Map<String, String>): Map<String, String> = properties
    .filterKeys { it.startsWith(REQUEST_PREFIX) }
    .mapKeys { it.key.removePrefix(REQUEST_PREFIX) }
    .filter { (sender, value) -> FILE_ID.matches(sender) && REQUEST_VALUE.matches(value) }

private fun sameContent(before: DriveFile, after: DriveFile) =
    before.md5Checksum != null && before.md5Checksum == after.md5Checksum && before.size == after.size

internal fun driveTestError(error: Exception): String = when (error) {
    is DriveException.AuthRequired -> "${error.reason}${error.statusCode?.let { " (Google status $it)" }.orEmpty()}"
    is DriveException.AuthorizationFailed -> "GOOGLE_AUTH_ERROR (Google status ${error.statusCode})"
    is DriveException.Http -> "HTTP ${error.code} ${error.reason.orEmpty()}"
    is ProbeRejected -> "PROBE_${error.code}"
    else -> error::class.simpleName ?: "Error"
}
