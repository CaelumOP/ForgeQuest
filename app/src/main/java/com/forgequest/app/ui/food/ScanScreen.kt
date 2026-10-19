package com.forgequest.app.ui.food

import com.forgequest.app.R
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.titleRes

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.FitSwitch
import android.content.Intent
import com.forgequest.app.ui.components.NumberEntryDialog
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.forgequest.app.AppContainer
import com.forgequest.app.data.FoodScan
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.goToTab
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.roundToInt

sealed interface ScanState {
    data object Camera : ScanState
    data class Analyzing(val photo: String) : ScanState
    data class Result(val photo: String?, val scan: FoodScan) : ScanState
    data class Failed(val photo: String?, val message: String, val keyProblem: Boolean = false) : ScanState
    /** The photo went to another AI app; its copied answer is read from the clipboard on return. */
    data class Outside(val photo: String, val wrongFormat: Boolean = false) : ScanState
}

/** How long the in-app scan may take before the screen points to another AI app instead. */
private const val SCAN_LIMIT_MS = 10_000L

enum class Portion(@androidx.annotation.StringRes val label: Int, val factor: Float) { SMALL(R.string.scan_portion_small, 0.75f), NORMAL(R.string.scan_normal, 1f), LARGE(R.string.scan_portion_large, 1.25f) }
enum class CookingFat(@androidx.annotation.StringRes val label: Int, val kcal: Int) { LIGHT(R.string.scan_fat_light, 0), NORMAL(R.string.scan_normal, 50), HEAVY(R.string.scan_fat_heavy, 150) }

class ScanViewModel(val app: AppContainer) : ViewModel() {
    val state = MutableStateFlow<ScanState>(ScanState.Camera)
    val hasKey: Boolean get() = app.settings.apiKey.value.isNotBlank()
    val logged = MutableStateFlow(false)
    var loggedSummary: String? = null
        private set

    fun analyzeBytes(bytes: ByteArray) = viewModelScope.launch {
        runCatching { app.food.savePhoto(bytes) }
            .onSuccess { analyze(it) }
            .onFailure { state.value = ScanState.Failed(null, (it as? AiError)?.userMessage ?: app.str(R.string.scan_err_save_photo)) }
    }

    fun analyzeUri(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { app.food.importPhoto(uri) }
            .onSuccess { analyze(it) }
            .onFailure { state.value = ScanState.Failed(null, (it as? AiError)?.userMessage ?: app.str(R.string.photo_err_open)) }
    }

    private var scanJob: Job? = null
    private var prompt: String? = null

    fun analyze(photo: String) {
        state.value = ScanState.Analyzing(photo)
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            // Raced against a timer rather than wrapped in withTimeout: a blocking request ignores cancellation,
            // and the screen shouldn't wait for it.
            val pending = async { runCatching { app.food.scan(photo) } }
            val outcome = withTimeoutOrNull(SCAN_LIMIT_MS) { pending.await() }
            if (outcome == null) pending.cancel()
            if ((state.value as? ScanState.Analyzing)?.photo != photo) return@launch
            val error = outcome?.exceptionOrNull()
            state.value = when {
                outcome == null -> ScanState.Failed(photo, app.str(R.string.scan_err_slow))
                error == null -> ScanState.Result(photo, outcome.getOrThrow())
                error is AiError -> ScanState.Failed(photo, error.userMessage, keyProblem = error is AiError.InvalidKey || error is AiError.MissingKey)
                else -> ScanState.Failed(photo, app.str(R.string.scan_err_generic))
            }
        }
    }

    /** Stops the in-app scan and hands the photo and prompt to [share], which opens the system share screen. */
    fun askOutside(photo: String, share: (android.net.Uri, String) -> Unit) = viewModelScope.launch {
        scanJob?.cancel()
        val text = app.food.outsidePrompt().also { prompt = it }
        runCatching { app.food.shareablePhoto(photo) }
            .onSuccess { uri ->
                state.value = ScanState.Outside(photo)
                share(uri, text)
            }
            .onFailure { state.value = ScanState.Failed(photo, app.str(R.string.scan_err_save_photo)) }
    }

    /** Called when the screen is back in focus after sharing: imports the AI app's answer if one was copied. */
    fun onReturn(clip: String?) {
        val s = state.value as? ScanState.Outside ?: return
        if (clip.isNullOrBlank() || clip == prompt) return
        val scan = app.food.parseScan(clip)
        state.value = if (scan != null) ScanState.Result(s.photo, scan) else s.copy(wrongFormat = true)
    }

    fun retake() {
        val photo = when (val s = state.value) {
            is ScanState.Analyzing -> s.photo
            is ScanState.Result -> s.photo
            is ScanState.Failed -> s.photo
            is ScanState.Outside -> s.photo
            ScanState.Camera -> null
        }
        scanJob?.cancel()
        state.value = ScanState.Camera
        viewModelScope.launch { app.food.discardPhoto(photo) }
    }

    fun log(photo: String?, name: String, category: FoodCategory, kcal: Int, protein: Float, carbs: Float, saveToFoods: Boolean, pin: Boolean) {
        viewModelScope.launch {
            app.food.logScannedMeal(name, category, kcal, protein, photo, carbs)
            if (saveToFoods) {
                app.food.saveFood(FoodEntity(name = name, category = category, kcal = kcal, protein = protein, carbs = carbs, serving = app.str(R.string.scan_default_serving), photoPath = photo, pinned = pin, createdAt = System.currentTimeMillis()))
            }
            val badges = app.progress.evaluateBadges()
            app.haptics.celebrate()
            loggedSummary = app.str(R.string.scan_logged_summary, name, kcal, protein.roundToInt()) + if (badges.isNotEmpty()) app.str(R.string.badge_unlocked_suffix, app.str(badges.first().titleRes)) else ""
            logged.value = true
        }
    }
}

@Composable
fun ScanScreen(nav: NavHostController) {
    val vm = appViewModel { ScanViewModel(it) }
    val state by vm.state.collectAsState()
    val logged by vm.logged.collectAsState()

    val snackbar = LocalSnackbar.current
    LaunchedEffect(logged) {
        if (logged) {
            nav.popBackStack()
            vm.loggedSummary?.let { snackbar.showSnackbar(it) }
        }
    }
    BackHandler { if (state is ScanState.Camera) nav.popBackStack() else vm.retake() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val askOutside: (String) -> Unit = { photo ->
        vm.askOutside(photo) { uri, prompt ->
            // Some AI apps drop the text that comes with a shared photo, so the prompt is on the clipboard too.
            clipboard.setText(AnnotatedString(prompt))
            val send = Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, prompt)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(null, uri)
            context.startActivity(Intent.createChooser(send, null))
        }
    }
    // The clipboard is only readable while the window has focus, so check when focus comes back from the other app.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(Unit) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused -> if (focused) vm.onReturn(clipboard.getText()?.text) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            ScanState.Camera -> CameraView(onCaptured = vm::analyzeBytes, onPicked = vm::analyzeUri, onClose = { nav.popBackStack() })
            is ScanState.Analyzing -> FrozenPhoto(s.photo) { ScanningOverlay(onAskOutside = { askOutside(s.photo) }) }
            is ScanState.Result -> FrozenPhoto(s.photo) {
                val carbsOn = vm.app.settings.settings.collectAsState(initial = null).value?.trackCarbs == true
                ResultSheet(s.scan, vm.app.food.categoryForNow(), carbsOn, onRetake = vm::retake) { name, cat, kcal, protein, carbs, save, pin ->
                    vm.log(s.photo, name, cat, kcal, protein, carbs, save, pin)
                }
            }
            is ScanState.Failed -> FrozenPhoto(s.photo) {
                ErrorCard(
                    s.message,
                    canRetry = s.photo != null,
                    keyProblem = s.keyProblem,
                    onRetry = { s.photo?.let(vm::analyze) },
                    onRetake = vm::retake,
                    onOpenProfile = { nav.popBackStack(); nav.goToTab(Routes.PROFILE) },
                    onAskOutside = s.photo?.let { photo -> { askOutside(photo) } },
                )
            }
            is ScanState.Outside -> FrozenPhoto(s.photo) {
                OutsideCard(s.wrongFormat, onRetake = vm::retake, onAskAgain = { askOutside(s.photo) })
            }
        }
        if (state !is ScanState.Camera) {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.cd_close), Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart)) { nav.popBackStack() }
        }
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun CameraView(onCaptured: (ByteArray) -> Unit, onPicked: (android.net.Uri) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    var captureFailed by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    // After "Don't allow" the system refuses to ask again; the only way back is the app's settings page.
    val askedBefore = remember { mutableStateOf(false) }
    val permanentlyDenied = !granted && askedBefore.value && activity != null &&
        !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    LaunchedEffect(Unit) { if (!granted) { askedBefore.value = true; permission.launch(Manifest.permission.CAMERA) } }

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
                            provider.unbindAll()
                            camera = runCatching { provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }.getOrNull()
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.scan_camera_off), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (permanentlyDenied) stringResource(R.string.scan_camera_denied)
                    else stringResource(R.string.scan_camera_ask),
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (permanentlyDenied) {
                    PillButton(stringResource(R.string.scan_open_settings), onClick = {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                        )
                    })
                } else {
                    PillButton(stringResource(R.string.scan_allow_camera), onClick = { askedBefore.value = true; permission.launch(Manifest.permission.CAMERA) })
                }
                Spacer(Modifier.height(10.dp))
                TonalPill(stringResource(R.string.photo_choose_gallery), onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, tint = Color.White)
            }
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            CircleButton(Icons.Rounded.Close, stringResource(R.string.cd_close), onClick = onClose)
            if (granted && camera?.cameraInfo?.hasFlashUnit() == true) {
                CircleButton(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, stringResource(R.string.cd_light)) {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .navigationBarsPadding().padding(top = 40.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (captureFailed) stringResource(R.string.scan_capture_failed)
                else stringResource(R.string.scan_frame_hint),
                style = MaterialTheme.typography.bodySmall, color = if (captureFailed) Fit.colors.coral else Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .clickable { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.photo_choose_gallery), tint = Color.White) }
                }
                // Shutter: white ring with a filled core, like the system camera.
                Box(
                    Modifier.size(78.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp).clip(CircleShape)
                        .background(if (shooting) Color.White.copy(alpha = 0.5f) else Color.White)
                        .clickable(enabled = granted && !shooting) {
                            shooting = true
                            val file = File(context.cacheDir, "capture.jpg")
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        shooting = false
                                        onCaptured(file.readBytes())
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
                Spacer(Modifier.weight(1f))
            }
        }
    }
    DisposableEffect(Unit) { onDispose { camera?.cameraControl?.enableTorch(false) } }
}

@Composable
private fun FrozenPhoto(photo: String?, overlay: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (photo != null) {
            AsyncImage(File(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        overlay()
    }
}

@Composable
private fun ScanningOverlay(onAskOutside: () -> Unit) {
    val c = Fit.colors
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    val steps = listOf(stringResource(R.string.scan_step_find), stringResource(R.string.scan_step_portions), stringResource(R.string.scan_step_add))
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (step < steps.lastIndex) { delay(2200); step++ } }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * (0.12f + 0.66f * sweep)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.teal.copy(alpha = 0.35f), Color.Transparent), startY = y - 120f, endY = y + 120f),
                topLeft = Offset(0f, y - 120f), size = androidx.compose.ui.geometry.Size(size.width, 240f),
            )
            drawLine(c.teal, Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), strokeWidth = 3.dp.toPx())
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp)
                .clip(RoundedCornerShape(28.dp)).background(c.card).padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(step, transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() }, label = "step") { i ->
                Text(steps[i], style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(Modifier.size(width = if (i == step) 22.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i <= step) c.teal else c.cardHigh))
                }
            }
            Spacer(Modifier.height(16.dp))
            TonalPill(stringResource(R.string.scan_ask_outside), onClick = onAskOutside, icon = Icons.Rounded.AutoAwesome)
        }
    }
}

@Composable
private fun ErrorCard(message: String, canRetry: Boolean, keyProblem: Boolean, onRetry: () -> Unit, onRetake: () -> Unit, onOpenProfile: () -> Unit, onAskOutside: (() -> Unit)?) {
    val c = Fit.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .clip(RoundedCornerShape(28.dp)).background(c.card).padding(24.dp),
        ) {
            Text(stringResource(R.string.scan_failed_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.scan_retake), onClick = onRetake, container = c.cardHigh, content = c.textPrimary)
                when {
                    keyProblem -> PillButton(stringResource(R.string.scan_open_profile), onClick = onOpenProfile)
                    canRetry -> PillButton(stringResource(R.string.action_try_again), onClick = onRetry)
                }
            }
            if (onAskOutside != null) {
                Spacer(Modifier.height(10.dp))
                PillButton(stringResource(R.string.scan_ask_outside), onClick = onAskOutside, icon = Icons.Rounded.AutoAwesome, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OutsideCard(wrongFormat: Boolean, onRetake: () -> Unit, onAskAgain: () -> Unit) {
    val c = Fit.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .clip(RoundedCornerShape(28.dp)).background(c.card).padding(24.dp),
        ) {
            Text(stringResource(R.string.scan_outside_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (wrongFormat) R.string.scan_outside_wrong_format else R.string.scan_outside_body),
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrongFormat) c.coral else c.textSecondary,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.scan_retake), onClick = onRetake, container = c.cardHigh, content = c.textPrimary)
                PillButton(stringResource(R.string.scan_ask_again), onClick = onAskAgain, icon = Icons.Rounded.AutoAwesome, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultSheet(
    scan: FoodScan,
    defaultCategory: FoodCategory,
    carbsOn: Boolean,
    onRetake: () -> Unit,
    onLog: (String, FoodCategory, Int, Float, Float, Boolean, Boolean) -> Unit,
) {
    val c = Fit.colors
    var items by remember { mutableStateOf(scan.items) }
    var dish by remember { mutableStateOf(scan.dish) }
    var included by remember { mutableStateOf(scan.items.indices.toSet()) }
    var editingGrams by remember { mutableStateOf<Int?>(null) }
    var portion by remember { mutableStateOf(Portion.NORMAL) }
    var fat by remember { mutableStateOf(CookingFat.LIGHT) }
    var category by remember { mutableStateOf(defaultCategory) }
    var save by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val chosen = items.filterIndexed { i, _ -> i in included }
    val kcal = (chosen.sumOf { it.kcal } * portion.factor).roundToInt() + if (chosen.isEmpty()) 0 else fat.kcal
    val protein = chosen.sumOf { it.protein.toDouble() }.toFloat() * portion.factor
    val carbs = chosen.sumOf { it.carbs.toDouble() }.toFloat() * portion.factor

    editingGrams?.let { index ->
        val item = items[index]
        NumberEntryDialog(
            title = stringResource(R.string.scan_grams_title, item.name), initial = item.grams.toFloat().coerceAtLeast(1f), unit = stringResource(R.string.unit_g), decimals = 0, min = 1f, max = 3000f,
            onDismiss = { editingGrams = null },
            onConfirm = { grams ->
                // Scale calories and protein with the weight so a corrected portion stays consistent.
                val factor = if (item.grams > 0) grams / item.grams else 1f
                items = items.toMutableList().also {
                    it[index] = item.copy(grams = grams.roundToInt(), kcal = (item.kcal * factor).roundToInt(), protein = item.protein * factor)
                }
                editingGrams = null
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = slideInVertically { it } + fadeIn(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.card)
                    .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.divider))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    BasicTextField(
                        value = dish,
                        onValueChange = { dish = it.take(48).replace("\n", "") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.teal),
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        decorationBox = { inner ->
                            Column {
                                inner()
                                Text(stringResource(R.string.scan_tap_name), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                            }
                        },
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.food_kcal, kcal), style = MaterialTheme.typography.titleMedium, color = c.blue, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.food_g_protein, protein.roundToInt()), style = MaterialTheme.typography.labelLarge, color = c.teal)
                        if (carbsOn) Text(stringResource(R.string.food_g_carbs, carbs.roundToInt()), style = MaterialTheme.typography.labelLarge, color = c.purple)
                    }
                }
                if (scan.handVisible) {
                    Text(stringResource(R.string.scan_hand_scaled), style = MaterialTheme.typography.bodySmall, color = c.teal)
                }
                if (scan.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(scan.note, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { i, item ->
                    val on = i in included
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(c.cardHigh)
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable { included = if (on) included - i else included + i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) c.teal else c.card), contentAlignment = Alignment.Center) {
                                if (on) Icon(Icons.Rounded.Check, contentDescription = if (on) stringResource(R.string.cd_included) else stringResource(R.string.cd_excluded), tint = c.onTeal, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { editingGrams = i }.padding(vertical = 4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = if (on) c.textPrimary else c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (item.grams > 0) stringResource(R.string.scan_about_grams, item.grams) else stringResource(R.string.scan_set_grams), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.food_kcal, item.kcal), style = MaterialTheme.typography.labelLarge, color = if (on) c.blue else c.textSecondary)
                            Text(stringResource(R.string.food_g, item.protein.roundToInt()), style = MaterialTheme.typography.labelMedium, color = if (on) c.teal else c.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.scan_portion_size), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedTabs(Portion.entries.map { stringResource(it.label) + " " + "${it.factor}×".replace(".0×", "×") }, Portion.entries.indexOf(portion), onSelect = { portion = Portion.entries[it] })
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.scan_extra_fat), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedTabs(CookingFat.entries.map { stringResource(it.label) + " +${it.kcal}" }, CookingFat.entries.indexOf(fat), onSelect = { fat = CookingFat.entries[it] })
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.scan_meal), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                SegmentedTabs(FoodCategory.entries.map { it.localLabel() }, FoodCategory.entries.indexOf(category), { category = FoodCategory.entries[it] })
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.scan_save_to_foods), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        Text(stringResource(R.string.scan_save_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    FitSwitch(save, { save = it })
                }
                if (save) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.food_pin_top), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                        FitSwitch(pin, { pin = it })
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Clear of the gesture bar; the Log button sat under the home handle.
                Row(Modifier.navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(stringResource(R.string.scan_retake), onClick = onRetake, container = c.cardHigh, content = c.textPrimary)
                    PillButton(stringResource(R.string.scan_log_meal), onClick = { onLog(dish.trim().ifBlank { scan.dish }, category, kcal, protein, carbs, save, pin) }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
