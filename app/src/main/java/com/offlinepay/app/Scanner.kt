package com.offlinepay.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(onPayee: (UpiPayee) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    var error by remember { mutableStateOf("") }
    val found = remember { AtomicBoolean(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    val pv = PreviewView(c)
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        val cp = future.get()
                        provider = cp
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(
                            ContextCompat.getMainExecutor(c),
                            qrAnalyzer(scanner) { raw ->
                                val payee = UpiParser.parse(raw)
                                if (payee == null) {
                                    error = tr(
                                        "Yeh UPI QR nahi hai",
                                        "यह UPI QR नहीं है"
                                    )
                                } else if (!found.getAndSet(true)) {
                                    onPayee(payee)
                                }
                            }
                        )
                        cp.unbindAll()
                        cp.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(c))
                    pv
                }
            )
        } else {
            Text(
                tr("Camera permission chahiye", "कैमरा अनुमति चाहिए"),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Scan frame
        Surface(
            modifier = Modifier.align(Alignment.Center).size(250.dp),
            color = Color.Transparent,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(3.dp, Color.White)
        ) {}

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BackRow(tr("QR scan karein", "QR स्कैन करें"), onBack, light = true)
        }
        Text(
            if (error.isNotEmpty()) error
            else tr("QR ko frame ke andar rakhein (internet nahi chahiye)", "QR को फ्रेम में रखें (इंटरनेट नहीं चाहिए)"),
            color = if (error.isNotEmpty()) Color(0xFFFFB4AB) else Color.White,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
        )
    }
}


@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun qrAnalyzer(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onRaw: (String) -> Unit
) = ImageAnalysis.Analyzer { proxy ->
    val media = proxy.image
    if (media == null) {
        proxy.close()
    } else {
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let(onRaw) }
            .addOnCompleteListener { proxy.close() }
    }
}
