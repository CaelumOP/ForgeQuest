package com.forgequest.app.ui.food

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.BarcodeResult
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.domain.Barcodes
import com.forgequest.app.domain.CatalogAmount
import com.forgequest.app.domain.CatalogFood
import com.forgequest.app.ui.components.MorphLoader
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

private sealed interface BarcodeState {
    data object Scanning : BarcodeState
    data object Looking : BarcodeState
    /** Found: choose the amount, then log. */
    data class Pick(val food: CatalogFood, val code: String) : BarcodeState
    data class Missing(val code: String, val offline: Boolean) : BarcodeState
    data class LabelCamera(val code: String) : BarcodeState
    data object Reading : BarcodeState
    data class LabelFailed(val code: String, val message: String) : BarcodeState
}

/** The scan, lookup and label-photo steps of one barcode scan. State lives in the composition, so each open starts fresh. */
private class BarcodeFlow(private val app: AppContainer, private val scope: CoroutineScope) {
    var state by mutableStateOf<BarcodeState>(BarcodeState.Scanning)
        private set

    fun onCode(raw: String, onOwn: (FoodEntity) -> Unit) {
        val code = Barcodes.clean(raw) ?: return
        if (state != BarcodeState.Scanning) return
        state = BarcodeState.Looking
        app.haptics.tick()
        scope.launch {
            state = when (val r = app.barcodes.find(code)) {
                is BarcodeResult.Own -> { onOwn(r.food); BarcodeState.Scanning }
                is BarcodeResult.Listed -> BarcodeState.Pick(r.food, code)
                is BarcodeResult.Online -> BarcodeState.Pick(r.food, code)
                BarcodeResult.NotFound -> BarcodeState.Missing(code, offline = false)
                BarcodeResult.Offline -> BarcodeState.Missing(code, offline = true)
            }
        }
    }

    fun scanAgain() { state = BarcodeState.Scanning }
    fun labelCamera(code: String) { state = BarcodeState.LabelCamera(code) }

    fun readLabel(code: String, jpeg: ByteArray) {
        state = BarcodeState.Reading
        scope.launch {
            var photo: String? = null
            state = try {
                photo = app.food.savePhoto(jpeg)
                val read = app.food.readLabel(photo, code)
                BarcodeState.Pick(if (read.nameEn.isBlank()) read.copy(nameEn = app.str(R.string.barcode_unnamed, code)) else read, code)
            } catch (e: Exception) {
                BarcodeState.LabelFailed(code, (e as? AiError)?.userMessage ?: app.str(R.string.barcode_err_label))
            } finally {
                app.food.discardPhoto(photo)
            }
        }
    }
}

/**
 * Full-screen barcode scanner over the Food screen. Lookup order: own foods, the bundled list, Open Food Facts; if none
 * has calories, a photo of the label is read instead. Found items go to the same amount step as the food list.
 */
@Composable
fun BarcodeScanScreen(
    onClose: () -> Unit,
    onOwn: (FoodEntity) -> Unit,
    onLog: (CatalogFood, CatalogAmount, String, String) -> Unit,
) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val flow = remember { BarcodeFlow(app, scope) }
    val aiOn = remember { app.settings.isAiOn() }
    val state = flow.state
    BackHandler { if (state is BarcodeState.Scanning || state is BarcodeState.Looking) onClose() else flow.scanAgain() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BarcodeCamera(
            detecting = state is BarcodeState.Scanning, labelMode = state is BarcodeState.LabelCamera, onClose = onClose,
            onCode = { flow.onCode(it, onOwn) },
            onPhoto = { jpeg -> (flow.state as? BarcodeState.LabelCamera)?.let { flow.readLabel(it.code, jpeg) } },
        )
        when (state) {
            BarcodeState.Scanning, is BarcodeState.LabelCamera, is BarcodeState.Pick -> Unit
            BarcodeState.Looking -> Busy(stringResource(R.string.barcode_looking))
            BarcodeState.Reading -> Busy(stringResource(R.string.barcode_reading))
            is BarcodeState.Missing -> ActionCard(
                title = stringResource(if (state.offline) R.string.barcode_offline_title else R.string.barcode_missing_title),
                body = stringResource(if (state.offline) R.string.barcode_offline_body else R.string.barcode_missing_body),
                // Reading a label photo needs the AI; with it off the choice is just try again or close.
                primary = stringResource(if (aiOn) R.string.barcode_label_photo else R.string.barcode_scan_again),
                onPrimary = { if (aiOn) flow.labelCamera(state.code) else flow.scanAgain() },
                secondary = stringResource(if (aiOn) R.string.barcode_scan_again else R.string.action_cancel),
                onSecondary = { if (aiOn) flow.scanAgain() else onClose() },
            )
            is BarcodeState.LabelFailed -> ActionCard(
                title = stringResource(R.string.barcode_label_failed), body = state.message,
                primary = stringResource(R.string.barcode_try_again), onPrimary = { flow.labelCamera(state.code) },
                secondary = stringResource(R.string.barcode_scan_again), onSecondary = flow::scanAgain,
            )
        }
    }
    (state as? BarcodeState.Pick)?.let { pick ->
        CatalogAmountSheet(pick.food, onLog = { amount, label -> onLog(pick.food, amount, label, pick.code) }, onDismiss = flow::scanAgain)
    }
}

@Composable
private fun BottomSlot(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.BottomCenter) { content() }

@Composable
private fun Busy(text: String) = BottomSlot {
    Row(
        Modifier.clip(ForgeShape.Md).background(Fit.colors.card).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MorphLoader(Fit.colors.teal, Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Fit.colors.textPrimary)
    }
}

@Composable
private fun ActionCard(title: String, body: String, primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) = BottomSlot {
    val c = Fit.colors
    Column(Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.card).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PillButton(primary, onPrimary, Modifier.fillMaxWidth(), icon = Icons.Rounded.PhotoCamera)
        Spacer(Modifier.height(8.dp))
        TonalPill(secondary, onSecondary, Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeCamera(detecting: Boolean, labelMode: Boolean, onClose: () -> Unit, onCode: (String) -> Unit, onPhoto: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val askedBefore = remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    // After "Don't allow" the system refuses to ask again; the only way back is the app's settings page.
    val permanentlyDenied = !granted && askedBefore.value && activity != null &&
        !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!granted) { askedBefore.value = true; permission.launch(Manifest.permission.CAMERA) } }

    val detectingNow = rememberUpdatedState(detecting)
    val codeNow = rememberUpdatedState(onCode)
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val analyzerThread = remember { Executors.newSingleThreadExecutor() }
    // The bundled model: works offline, nothing is downloaded. Only shop-product formats, so QR codes are ignored.
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    var captureFailed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { camera?.cameraControl?.enableTorch(false); scanner.close(); analyzerThread.shutdown() } }

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(analyzerThread) { proxy ->
                                val image = proxy.image
                                if (image == null || !detectingNow.value) { proxy.close(); return@setAnalyzer }
                                scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                                    .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue?.let(Barcodes::clean) }?.let { codeNow.value(it) } }
                                    .addOnCompleteListener { proxy.close() }
                            }
                            provider.unbindAll()
                            camera = runCatching { provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture) }.getOrNull()
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // No aiming box: the reader looks at the whole picture, so the barcode can be anywhere in view.
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.scan_camera_off), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (permanentlyDenied) R.string.scan_camera_denied else R.string.scan_camera_ask),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (permanentlyDenied) {
                    PillButton(stringResource(R.string.scan_open_settings), onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
                    })
                } else {
                    PillButton(stringResource(R.string.scan_allow_camera), onClick = { askedBefore.value = true; permission.launch(Manifest.permission.CAMERA) })
                }
            }
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton(Icons.Rounded.Close, stringResource(R.string.cd_close), onClose)
            if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                RoundButton(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, stringResource(R.string.cd_light)) {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }
            }
        }

        if (detecting || labelMode) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .navigationBarsPadding().padding(top = 40.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (captureFailed) stringResource(R.string.scan_capture_failed)
                    else stringResource(if (labelMode) R.string.barcode_label_hint else R.string.barcode_hint),
                    style = MaterialTheme.typography.bodySmall, color = if (captureFailed) Fit.colors.coral else Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                if (labelMode) {
                    Spacer(Modifier.height(16.dp))
                    // Shutter: white ring with a filled core, like the meal camera.
                    val shutterCd = stringResource(R.string.photo_take)
                    Box(
                        Modifier.size(78.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp).clip(CircleShape)
                            .background(if (shooting) Color.White.copy(alpha = 0.5f) else Color.White)
                            .semantics { contentDescription = shutterCd }
                            .clickable(enabled = granted && !shooting, role = Role.Button) {
                                shooting = true
                                val file = File(context.cacheDir, "label.jpg")
                                capture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            shooting = false
                                            captureFailed = false
                                            onPhoto(file.readBytes())
                                            file.delete()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            shooting = false
                                            captureFailed = true
                                        }
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}
