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
import app.kinjar.data.sync.drive.DriveTransport
import app.kinjar.data.sync.drive.DriveException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class DriveTestState(
    val busy: Boolean = false,
    val connected: Boolean = false,
    val accountName: String? = null,
    val folderId: String? = null,
    val files: List<String> = emptyList(),
    val error: String? = null,
    val shared: Boolean = false,
    val resolution: IntentSenderRequest? = null,
)

@HiltViewModel
class DriveTestViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("drive_test", Context.MODE_PRIVATE)
    private var account: Account? = prefs.getString("account", null)?.let { Account(it, "com.google") }
    private val mutable = MutableStateFlow(DriveTestState(
        connected = account != null,
        accountName = account?.name,
        folderId = if (account != null) prefs.getString("folder", null) else null,
    ))
    val state = mutable.asStateFlow()
    private val auth = DriveAuthorizer(context) { account ?: throw DriveException.AuthRequired("Select an account") }
    private val drive = DriveTransport(auth)
    private var probeName = prefs.getString("probe_name", null) ?: "kinjar-probe-${UUID.randomUUID()}.txt"
    private var probeId: String? = if (account != null) prefs.getString("probe", null) else null

    fun connect() = run { accept(auth.authorize(auth.silentRequest(account))) }
    fun pickFolder() = run { accept(auth.authorize(auth.pickerRequest())) }
    fun resolve(code: Int, data: Intent?) = run { accept(auth.resultFromActivity(code, data)) }
    fun resolutionLaunched() { mutable.update { it.copy(resolution = null) } }

    fun createFolder() = run {
        val folder = drive.createFolder("kinjar-sync-test-${UUID.randomUUID()}")
        probeId = null
        mutable.update { it.copy(folderId = folder.id, files = emptyList(), shared = false) }
        save()
    }

    fun share(email: String) = run {
        val id = requireNotNull(state.value.folderId)
        requireTestFolder(id)
        drive.shareWithEmail(id, email.trim())
        mutable.update { it.copy(shared = true) }
    }

    fun writeProbe() = run { write() }
    fun writeNewProbe() = run {
        probeId = null
        probeName = "kinjar-probe-${UUID.randomUUID()}.txt"
        write()
    }

    private suspend fun write() {
        val folder = requireNotNull(state.value.folderId)
        requireTestFolder(folder)
        val bytes = "kinjar connectivity test\n${Instant.now()}".encodeToByteArray()
        val id = probeId
        probeId = if (id == null) drive.createFile(probeName, folder, bytes).id else drive.updateFile(id, bytes).id
        save()
        readProbes(folder)
    }

    fun refresh() = run { readProbes(requireNotNull(state.value.folderId)) }

    fun revoke() = run {
        auth.revoke(requireNotNull(account))
        account = null
        probeId = null
        mutable.update { it.copy(connected = false, accountName = null, folderId = null, files = emptyList(), shared = false) }
        save()
    }

    private suspend fun accept(outcome: DriveAuthorizer.Outcome) {
        when (outcome) {
            is DriveAuthorizer.Outcome.NeedsResolution -> mutable.update { it.copy(resolution = outcome.request) }
            is DriveAuthorizer.Outcome.Granted -> {
                val selected = outcome.account ?: throw DriveException.AuthRequired("Account identity unavailable")
                if (account != selected) {
                    probeId = null
                    mutable.update { it.copy(folderId = null, files = emptyList(), shared = false) }
                }
                account = selected
                mutable.update { it.copy(connected = true, accountName = selected.name) }
                save()
                outcome.pickedFileIds.singleOrNull()?.let { id ->
                    requireTestFolder(id)
                    probeId = null
                    mutable.update { it.copy(folderId = id, files = emptyList(), shared = false) }
                    save()
                    readProbes(id)
                }
            }
        }
    }

    private suspend fun requireTestFolder(id: String) {
        val folder = drive.metadata(id)
        require(folder.mimeType == DriveTransport.FOLDER_MIME && folder.name.startsWith("kinjar-sync-test-"))
    }

    private suspend fun readProbes(folder: String) {
        requireTestFolder(folder)
        val files = drive.listChildren(folder, maxFiles = 100).filter { it.name.startsWith("kinjar-probe-") && it.name.endsWith(".txt") }
        val readable = files.map {
            val bytes = drive.download(it.id, 1024)
            require(bytes.decodeToString().startsWith("kinjar connectivity test\n"))
            "${it.name}\nID: ${it.id}\nVersion: ${it.version ?: "?"}"
        }
        mutable.update { it.copy(files = readable) }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        check(prefs.edit().putString("account", account?.name).putString("folder", state.value.folderId)
            .putString("probe", probeId).putString("probe_name", probeName).commit())
    }

    private fun run(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = if (e is DriveException.Http) "HTTP ${e.code} ${e.reason.orEmpty()}" else e::class.simpleName ?: "Error"
                mutable.update { it.copy(error = detail) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
