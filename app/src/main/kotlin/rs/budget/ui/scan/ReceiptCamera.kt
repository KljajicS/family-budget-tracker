package rs.budget.ui.scan

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import rs.budget.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import zxingcpp.BarcodeReader

@Composable
fun ReceiptCamera(modifier: Modifier, onCode: (String) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val codeCallback by rememberUpdatedState(onCode)
    val errorCallback by rememberUpdatedState(onError)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }

    DisposableEffect(camera, owner) {
        val state = camera?.cameraInfo?.torchState
        val observer = Observer<Int> { torch = it == TorchState.ON }
        torch = state?.value == TorchState.ON
        state?.observe(owner, observer)
        onDispose { state?.removeObserver(observer) }
    }

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val disposed = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val reader by lazy { BarcodeReader(BarcodeReader.Options(formats = setOf(BarcodeReader.Format.QR_CODE), tryRotate = true, tryHarder = true)) }
        analysis.setAnalyzer(executor) { frame ->
            try {
                if (!disposed.get() && !delivered.get()) {
                    val value = reader.read(frame).firstOrNull { it.error == null && !it.text.isNullOrBlank() }?.text
                    if (value != null && delivered.compareAndSet(false, true)) {
                        main.execute { if (!disposed.get()) codeCallback(value) }
                    }
                }
            } catch (_: Exception) {
                if (delivered.compareAndSet(false, true)) main.execute { if (!disposed.get()) errorCallback() }
            } catch (_: LinkageError) {
                if (delivered.compareAndSet(false, true)) main.execute { if (!disposed.get()) errorCallback() }
            } finally {
                frame.close()
            }
        }
        providerFuture.addListener({
            if (!disposed.get()) {
                try {
                    camera = providerFuture.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    errorCallback()
                }
            }
        }, main)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            if (providerFuture.isDone) runCatching { providerFuture.get().unbind(preview, analysis) }
            camera = null
            executor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            Box(Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                CameraButton(if (torch) Icons.Default.FlashOff else Icons.Default.FlashOn,
                    stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on)) { camera?.cameraControl?.enableTorch(!torch) }
            }
        }
    }
}

@Composable
internal fun CameraButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).background(Color(0xFF303234), CircleShape)
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
