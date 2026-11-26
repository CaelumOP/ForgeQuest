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
    internal const val PREFS = "ui_prefs"
    const val KEY_LANGUAGE_CHOSEN = "language_chosen"
    const val KEY_TOUR_SEEN = "tour_seen"
    const val KEY_GS_ELIGIBLE = "gs_eligible"
    const val KEY_GS_DISMISSED = "gs_dismissed"
    const val KEY_GS_FRIEND_DONE = "gs_friend_done"
    const val KEY_FIRST_TICK_DONE = "first_tick_done"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun languageChosen(c: Context) = prefs(c).getBoolean(KEY_LANGUAGE_CHOSEN, false)
    fun setLanguageChosen(c: Context) = prefs(c).edit().putBoolean(KEY_LANGUAGE_CHOSEN, true).apply()
    fun tourSeen(c: Context) = prefs(c).getBoolean(KEY_TOUR_SEEN, false)
    fun setTourSeen(c: Context) = prefs(c).edit().putBoolean(KEY_TOUR_SEEN, true).apply()
    fun firstTickDone(c: Context) = prefs(c).getBoolean(KEY_FIRST_TICK_DONE, false)
    fun setFirstTickDone(c: Context) = prefs(c).edit().putBoolean(KEY_FIRST_TICK_DONE, true).apply()

    fun gsEligible(c: Context) = prefs(c).getBoolean(KEY_GS_ELIGIBLE, false)
    fun setGsEligible(c: Context) = prefs(c).edit().putBoolean(KEY_GS_ELIGIBLE, true).apply()
    fun gsDismissed(c: Context) = prefs(c).getBoolean(KEY_GS_DISMISSED, false)
    fun setGsDismissed(c: Context) = prefs(c).edit().putBoolean(KEY_GS_DISMISSED, true).apply()
    fun gsFriendDone(c: Context) = prefs(c).getBoolean(KEY_GS_FRIEND_DONE, false)
    fun setGsFriendDone(c: Context) = prefs(c).edit().putBoolean(KEY_GS_FRIEND_DONE, true).apply()
}

/**
 * The first-visit tour: one tip per main tab, shown next to the one button that matters there, only after the first
 * set tick (Home: start the workout; Workouts: change plan; Food: search and log; Profile; Progress). Optional things,
 * such as the Fitbit readiness row, get no tip. The tips themselves are drawn by `CoachMarkBox`; this object only knows
 * which ones make up the tour and when it is over.
 */
object AppTour {
    const val HOME = "coach_home"
    const val WORKOUTS = "coach_plan"
    const val FOOD = "coach_food"
    const val PROFILE = "coach_profile"
    const val PROGRESS = "coach_progress"
    val ALL_COACH_KEYS = listOf(HOME, WORKOUTS, FOOD, PROFILE, PROGRESS)

    /** Called after a tip was shown: once every step has been seen, the tour is marked finished for good. */
    fun stepSeen(c: Context) {
        val prefs = c.getSharedPreferences(TourPrefs.PREFS, Context.MODE_PRIVATE)
        if (ALL_COACH_KEYS.all { prefs.getBoolean(it, false) }) TourPrefs.setTourSeen(c)
    }

    /** "Skip tips" on any tip: no more of them. Returns true if tips were not already skipped. */
    fun skip(c: Context): Boolean {
        val newlySkipped = !TourPrefs.tourSeen(c)
        TourPrefs.setTourSeen(c)
        return newlySkipped
    }

    /** Clears all coach-mark seen keys and `tour_seen` so tips appear again on first visit. */
    fun resetTips(c: Context) {
        val editor = c.getSharedPreferences(TourPrefs.PREFS, Context.MODE_PRIVATE).edit()
        ALL_COACH_KEYS.forEach { editor.remove(it) }
        editor.remove(TourPrefs.KEY_TOUR_SEEN)
        editor.apply()
    }
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
