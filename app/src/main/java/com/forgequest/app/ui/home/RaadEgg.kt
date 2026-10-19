package com.forgequest.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** Easter egg: the first time a name with «رعد» or "r3d" is saved, a banner sweeps across the screen with confetti. Tap to dismiss. */
@Composable
fun RaadEgg(name: String, onBuzz: () -> Unit) {
    val hit = "رعد" in name || name.contains("r3d", ignoreCase = true)
    // Once per saved name, ever: remembered on the phone so it doesn't replay on every open.
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("egg", android.content.Context.MODE_PRIVATE)
    var show by remember(name) { mutableStateOf(hit && prefs.getString("shown_for", null) != name) }
    if (!show) return
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        prefs.edit().putString("shown_for", name).apply()
        onBuzz()
        launch { kotlinx.coroutines.delay(1500); onBuzz() }
        sweep.animateTo(1f, tween(3200, easing = LinearEasing))
        show = false
    }
    val bits = remember { List(40) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextInt(5)) } }
    val colors = listOf(Color(0xFFFFC107), Color(0xFF00BFA5), Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFE040FB))
    Popup(alignment = Alignment.Center, onDismissRequest = { show = false }) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).clickable { show = false }, contentAlignment = Alignment.Center) {
            val t = sweep.value
            Canvas(Modifier.fillMaxSize()) {
                bits.forEach { (x, y, k) ->
                    val px = size.width * ((x + t * 0.3f) % 1f)
                    val py = size.height * ((y + t * 1.6f) % 1f)
                    drawCircle(colors[k], radius = 5.dp.toPx() + 3.dp.toPx() * sin((t * 20 + x * 6).toDouble()).toFloat(), center = Offset(px, py))
                }
            }
            // Right to left across the screen, with a little wobble and pulse on the way.
            val w = constraints.maxWidth.toFloat()
            Box(
                Modifier.wrapContentWidth(unbounded = true).graphicsLayer {
                    translationX = w * (1.1f - 2.2f * t)
                    rotationZ = -6f + 4f * sin(t * 25f)
                    val pulse = 1f + 0.08f * sin(t * 40f)
                    scaleX = pulse; scaleY = pulse
                }.background(Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFFFC107), Color(0xFF00BFA5))), RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text("اسامه عمك!!!", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl))
            }
        }
    }
}
