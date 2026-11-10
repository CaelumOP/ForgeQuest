package com.forgequest.app.ui.onboarding

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forgequest.app.AppLocale
import com.forgequest.app.R
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit

/**
 * First-run flags kept next to the language choice, so they survive the activity restart a language change causes.
 * The page-by-page tour is gone (tips now appear next to the buttons they explain); "tour_seen" is still read so
 * people who finished the old tour don't get the tips again.
 */
object TourPrefs {
    private const val PREFS = "ui_prefs"
    fun languageChosen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("language_chosen", false)
    fun setLanguageChosen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("language_chosen", true).apply()
    fun tourSeen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("tour_seen", false)
    fun setTourSeen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tour_seen", true).apply()
}

/**
 * The first-visit tour: one tip per main tab, shown next to the one button that matters there, the first time that tab
 * is opened (Home: start the workout; Workouts: change plan; Food: search and log; Coach: ask), plus the set tick inside
 * a workout. Optional things, such as the Fitbit readiness row, get no tip. The tips themselves are drawn by
 * `CoachMarkBox`; this object only knows which ones make up the tour and when it is over.
 */
object AppTour {
    const val HOME = "coach_home"
    const val WORKOUTS = "coach_plan"
    const val FOOD = "coach_food"
    const val COACH = "coach_ask"
    const val SET_TICK = "coach_tick"
    private val steps = listOf(HOME, WORKOUTS, FOOD, COACH, SET_TICK)
    private const val PREFS = "ui_prefs"

    /** Called after a tip was shown: once every step has been seen, the tour is marked finished for good. */
    fun stepSeen(c: Context) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (steps.all { prefs.getBoolean(it, false) }) TourPrefs.setTourSeen(c)
    }

    /** "Skip tips" on any tip: no more of them. */
    fun skip(c: Context) = TourPrefs.setTourSeen(c)
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
        Text(stringResource(R.string.lang_choose_en), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.lang_choose_ar), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        PillButton(stringResource(R.string.lang_name_en), onClick = { choose("en") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        PillButton(stringResource(R.string.lang_name_ar), onClick = { choose("ar") }, modifier = Modifier.fillMaxWidth())
    }
}
