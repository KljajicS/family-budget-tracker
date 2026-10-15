package rs.budget.ui.scan

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            TextButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Text(stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on))
            }
        }
    }
}
