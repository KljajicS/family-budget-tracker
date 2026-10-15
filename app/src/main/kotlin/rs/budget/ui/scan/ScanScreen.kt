package rs.budget.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.ui.add.AddContent
import rs.budget.ui.add.AddViewModel
import rs.budget.ui.theme.LocalColors
import rs.budget.ui.theme.PrimaryButton
import rs.budget.ui.theme.sans

@Composable
fun ScanScreen(onSaved: () -> Unit, onManual: () -> Unit, vm: ScanViewModel = hiltViewModel(), add: AddViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val draft by add.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(cameraGranted()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val c = LocalColors.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = cameraGranted() }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) vm.leave()
        }
    }
    LaunchedEffect(s.receipt?.id) { s.receipt?.let { add.prefillReceipt(it, s.categoryId) } }
    LaunchedEffect(draft.saved) {
        if (draft.saved) { vm.reset(); add.resetReceipt(); onSaved() }
    }
    fun reset() { if (!draft.saving) { vm.reset(); add.resetReceipt() } }
    BackHandler(s.stage != ScanStage.CAPTURING) { reset() }

    if (s.stage == ScanStage.REVIEW && draft.receipt != null) {
        AddContent(draft, ::reset, add::setType, add::press, add::setCategory, add::setAccount,
            add::setToAccount, add::setDate, add::setMerchant, add::skip, add::save)
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.scan_title), style = sans(24), color = c.text)
        when (s.stage) {
            ScanStage.CAPTURING -> if (granted) {
                Text(stringResource(R.string.scan_hint), style = sans(14), color = c.muted)
                ReceiptCamera(Modifier.fillMaxWidth().weight(1f), vm::scanned, vm::cameraFailed)
            } else {
                Text(stringResource(R.string.scan_permission), color = c.text)
                PrimaryButton(stringResource(R.string.scan_allow_camera), onClick = { requested = true; permission.launch(Manifest.permission.CAMERA) })
                if (requested) TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.scan_open_settings)) }
            }
            ScanStage.LOADING, ScanStage.REVIEW -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.scan_loading), color = c.text)
                TextButton(onClick = ::reset) { Text(stringResource(R.string.scan_cancel)) }
            }
            ScanStage.ERROR -> {
                Text(stringResource(s.error ?: R.string.scan_parse_error), color = c.orange)
                if (s.canRetry) PrimaryButton(stringResource(R.string.scan_retry), onClick = vm::retry)
                TextButton(onClick = ::reset) { Text(stringResource(R.string.scan_again)) }
            }
        }
        TextButton(onClick = { reset(); onManual() }) { Text(stringResource(R.string.scan_manual)) }
    }
}
