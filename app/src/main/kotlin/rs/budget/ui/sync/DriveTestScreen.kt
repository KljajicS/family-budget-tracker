package rs.budget.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.ui.settings.ScreenHeader
import rs.budget.ui.theme.FieldBox
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.PrimaryButton

@Composable
fun DriveTestScreen(onBack: () -> Unit, vm: DriveTestViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = LocalColors.current
    var email by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.resolve(it.resultCode, it.data)
    }
    LaunchedEffect(state.resolution) {
        state.resolution?.let { launcher.launch(it); vm.resolutionLaunched() }
    }
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.sync_test_title), onBack)
        Text(stringResource(R.string.sync_test_description), color = c.muted)
        PrimaryButton(stringResource(R.string.sync_test_connect), !state.busy, vm::connect)
        state.accountName?.let { Text(stringResource(R.string.sync_test_account, it), color = c.text) }
        if (state.connected) {
            PrimaryButton(stringResource(R.string.sync_test_create), !state.busy, vm::createFolder)
            PrimaryButton(stringResource(R.string.sync_test_pick), !state.busy, vm::pickFolder)
            PrimaryButton(stringResource(R.string.sync_test_revoke), !state.busy, vm::revoke)
        }
        state.folderId?.let {
            Text(stringResource(R.string.sync_test_folder, it), color = c.text)
            FieldBox(stringResource(R.string.sync_test_email), email, { email = it })
            PrimaryButton(stringResource(R.string.sync_test_share), !state.busy && email.isNotBlank()) { vm.share(email) }
            if (state.shared) Text(stringResource(R.string.sync_test_shared), color = c.muted)
            PrimaryButton(stringResource(R.string.sync_test_write), !state.busy, vm::writeProbe)
            PrimaryButton(stringResource(R.string.sync_test_new_file), !state.busy, vm::writeNewProbe)
            PrimaryButton(stringResource(R.string.sync_test_read), !state.busy, vm::refresh)
            Text(stringResource(R.string.sync_test_count, state.files.size), color = c.text)
            state.files.forEach { name -> Text(name, color = c.muted) }
        }
        if (state.busy) Text(stringResource(R.string.sync_test_busy), color = c.muted)
        state.error?.let { Text(stringResource(R.string.sync_test_failed) + "\n" + it, color = c.muted) }
    }
}
