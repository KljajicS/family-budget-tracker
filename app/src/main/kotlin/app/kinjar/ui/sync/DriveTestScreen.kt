package app.kinjar.ui.sync

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kinjar.R
import app.kinjar.ui.settings.ScreenHeader
import app.kinjar.ui.theme.FieldBox
import app.kinjar.ui.theme.LocalColors
import app.kinjar.ui.theme.PrimaryButton
import app.kinjar.ui.theme.SectionStyle
import kotlinx.coroutines.launch

@Composable
fun DriveTestScreen(onBack: () -> Unit, vm: DriveTestViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var peerId by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.resolve(it.resultCode, it.data)
    }
    LaunchedEffect(state.resolution) {
        state.resolution?.let { launcher.launch(it); vm.resolutionLaunched() }
    }
    val idle = !state.busy
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.sync_test_title), onBack)
        Text(stringResource(R.string.sync_test_description), color = c.muted)
        Text(stringResource(R.string.sync_test_steps), color = c.text)
        PrimaryButton(stringResource(R.string.sync_test_connect), idle, vm::connect)
        state.accountName?.let { Text(stringResource(R.string.sync_test_account, it), color = c.text) }
        if (state.connected) {
            Text(stringResource(R.string.sync_test_own_section), style = SectionStyle, color = c.text)
            val own = state.ownFileId
            if (own == null) {
                PrimaryButton(stringResource(R.string.sync_test_create_own), idle, vm::createOwnFile)
            } else {
                SelectionContainer { Text(stringResource(R.string.sync_test_own_id, own), color = c.text) }
                PrimaryButton(stringResource(R.string.sync_test_copy_id)) {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Drive file ID", own))) }
                }
                PrimaryButton(stringResource(R.string.sync_test_update_own), idle, vm::updateOwnFile)
                FieldBox(stringResource(R.string.sync_test_email), email, { email = it })
                PrimaryButton(stringResource(R.string.sync_test_share), idle && email.isNotBlank()) { vm.share(email) }
                PrimaryButton(stringResource(R.string.sync_test_read_mailbox), idle, vm::readMailbox)
            }

            Text(stringResource(R.string.sync_test_peer_section), style = SectionStyle, color = c.text)
            FieldBox(stringResource(R.string.sync_test_peer_id_field), peerId, { peerId = it })
            PrimaryButton(stringResource(R.string.sync_test_grant), idle && peerId.isNotBlank()) { vm.grantPeer(peerId) }
            state.peerFileId?.let { peer ->
                SelectionContainer { Text(stringResource(R.string.sync_test_peer_id, peer), color = c.text) }
                PrimaryButton(stringResource(R.string.sync_test_read), idle, vm::readPeer)
                if (own != null) {
                    PrimaryButton(stringResource(R.string.sync_test_send_request), idle, vm::sendRequest)
                    PrimaryButton(stringResource(R.string.sync_test_remove_request), idle, vm::removeRequest)
                }
            }
            PrimaryButton(stringResource(R.string.sync_test_revoke), idle, vm::revoke)
        }
        if (state.report.isNotEmpty()) {
            Text(stringResource(R.string.sync_test_result), style = SectionStyle, color = c.text)
            SelectionContainer { Text(state.report.joinToString("\n"), color = c.muted) }
        }
        if (state.busy) Text(stringResource(R.string.sync_test_busy), color = c.muted)
        state.error?.let { Text(stringResource(R.string.sync_test_failed) + "\n" + it, color = c.muted) }
    }
}
