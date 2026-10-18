package com.forgequest.app.ui.onboarding

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forgequest.app.AppLocale
import com.forgequest.app.R
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.launch

/** First-run flags kept next to the language choice, so they survive the activity restart a language change causes. */
object TourPrefs {
    private const val PREFS = "ui_prefs"
    fun languageChosen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("language_chosen", false)
    fun setLanguageChosen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("language_chosen", true).apply()
    fun tourSeen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("tour_seen", false)
    fun setTourSeen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tour_seen", true).apply()
}

/** The very first screen: both languages side by side, since the person may not read the phone's language yet. */
@Composable
fun LanguageChoice(onChosen: () -> Unit) {
    val c = Fit.colors
    val context = LocalContext.current
    fun choose(tag: String) {
        TourPrefs.setLanguageChosen(context)
        if (AppLocale.current(context) != tag) {
            AppLocale.set(context, tag)
            (context as? Activity)?.recreate()
        } else onChosen()
    }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Choose your language", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center)
        Text("اختر لغتك", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        PillButton("English", onClick = { choose("en") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        PillButton("العربية", onClick = { choose("ar") }, modifier = Modifier.fillMaxWidth())
    }
}

private data class Slide(val icon: ImageVector, val color: @Composable () -> Color, val title: Int, val body: Int)

private val slides = listOf(
    Slide(Icons.Rounded.Spa, { Fit.colors.teal }, R.string.tour_1_t, R.string.tour_1_b),
    Slide(Icons.Rounded.LocalFireDepartment, { Fit.colors.gold }, R.string.tour_2_t, R.string.tour_2_b),
    Slide(Icons.Rounded.FitnessCenter, { Fit.colors.teal }, R.string.tour_3_t, R.string.tour_3_b),
    Slide(Icons.Rounded.CameraAlt, { Fit.colors.coral }, R.string.tour_4_t, R.string.tour_4_b),
    Slide(Icons.Rounded.AutoAwesome, { Fit.colors.purple }, R.string.tour_5_t, R.string.tour_5_b),
    Slide(Icons.Rounded.MonitorHeart, { Fit.colors.coral }, R.string.tour_6_t, R.string.tour_6_b),
    Slide(Icons.Rounded.EmojiEvents, { Fit.colors.gold }, R.string.tour_7_t, R.string.tour_7_b),
)

/** Seven feature slides with Skip, page dots and Next; the last button leads into setup (or back, on a replay). */
@Composable
fun AppTour(finishLabel: String, onDone: () -> Unit) {
    val c = Fit.colors
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.tour_skip), color = c.textSecondary) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val s = slides[page]
            val tint = s.color()
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(132.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(s.icon, contentDescription = null, tint = tint, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(36.dp))
                Text(stringResource(s.title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Text(stringResource(s.body), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(slides.size) { i ->
                    val on = i == pager.currentPage
                    Box(Modifier.height(8.dp).width(if (on) 22.dp else 8.dp).clip(CircleShape).background(if (on) c.teal else c.cardHigh))
                }
            }
            Spacer(Modifier.weight(1f))
            PillButton(
                if (last) finishLabel else stringResource(R.string.tour_next),
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            )
        }
    }
}
