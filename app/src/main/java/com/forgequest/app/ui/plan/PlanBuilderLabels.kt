package com.forgequest.app.ui.plan

import androidx.annotation.StringRes
import com.forgequest.app.R
import com.forgequest.app.domain.Cardio
import com.forgequest.app.domain.DayFocus
import com.forgequest.app.domain.Experience
import com.forgequest.app.domain.Injury
import com.forgequest.app.domain.RepPref
import com.forgequest.app.domain.RestPref
import com.forgequest.app.domain.TrainGoal
import com.forgequest.app.domain.WhyTag

/** The builder's enums are text-free; these give each one its string (EN and AR live in the resource files). */
@StringRes
internal fun TrainGoal.pbRes(): Int = when (this) {
    TrainGoal.STRENGTH -> R.string.pb_goal_strength
    TrainGoal.MUSCLE -> R.string.pb_goal_muscle
    TrainGoal.FAT_LOSS -> R.string.pb_goal_fat
    TrainGoal.ENDURANCE -> R.string.pb_goal_endurance
    TrainGoal.GENERAL -> R.string.pb_goal_general
}

@StringRes
internal fun TrainGoal.descRes(): Int = when (this) {
    TrainGoal.STRENGTH -> R.string.pb_goal_strength_desc
    TrainGoal.MUSCLE -> R.string.pb_goal_muscle_desc
    TrainGoal.FAT_LOSS -> R.string.pb_goal_fat_desc
    TrainGoal.ENDURANCE -> R.string.pb_goal_endurance_desc
    TrainGoal.GENERAL -> R.string.pb_goal_general_desc
}

@StringRes
internal fun Experience.pbRes(): Int = when (this) {
    Experience.NEW -> R.string.pb_exp_new
    Experience.SOME -> R.string.pb_exp_some
    Experience.SEASONED -> R.string.pb_exp_seasoned
}

@StringRes
internal fun Injury.pbRes(): Int = when (this) {
    Injury.SHOULDER -> R.string.pb_injury_shoulder
    Injury.KNEE -> R.string.pb_injury_knee
    Injury.LOWER_BACK -> R.string.pb_injury_lower_back
    Injury.WRIST -> R.string.pb_injury_wrist
    Injury.ELBOW -> R.string.pb_injury_elbow
    Injury.NECK -> R.string.pb_injury_neck
}

@StringRes
internal fun RepPref.pbRes(): Int = when (this) {
    RepPref.AUTO -> R.string.pb_auto
    RepPref.LOW -> R.string.pb_reps_low
    RepPref.MID -> R.string.pb_reps_mid
    RepPref.HIGH -> R.string.pb_reps_high
}

@StringRes
internal fun RestPref.pbRes(): Int = when (this) {
    RestPref.AUTO -> R.string.pb_auto
    RestPref.SHORT -> R.string.pb_rest_short
    RestPref.LONG -> R.string.pb_rest_long
}

@StringRes
internal fun Cardio.pbRes(): Int = when (this) {
    Cardio.NONE -> R.string.pb_cardio_none
    Cardio.FINISHER -> R.string.pb_cardio_finisher
    Cardio.OWN_DAY -> R.string.pb_cardio_own
}

@StringRes
internal fun DayFocus.pbRes(): Int = when (this) {
    DayFocus.FULL_BODY -> R.string.pb_day_full
    DayFocus.UPPER -> R.string.pb_day_upper
    DayFocus.LOWER -> R.string.pb_day_lower
    DayFocus.PUSH -> R.string.pb_day_push
    DayFocus.PULL -> R.string.pb_day_pull
    DayFocus.LEGS -> R.string.pb_day_legs
    DayFocus.CONDITIONING -> R.string.pb_day_cond
}

@StringRes
internal fun WhyTag.pbRes(): Int = when (this) {
    WhyTag.SKIPPED_FOR_INJURY -> R.string.pb_why_injury
    WhyTag.NO_EQUIPMENT -> R.string.pb_why_equipment
    WhyTag.FOCUS_BOOST -> R.string.pb_why_focus
    WhyTag.TIME_TRIMMED -> R.string.pb_why_time
    WhyTag.TIER_FROM_MAX -> R.string.pb_why_tier
    WhyTag.HOLD_FALLBACK -> R.string.pb_why_hold
    WhyTag.CARDIO_ADDED -> R.string.pb_why_cardio
    WhyTag.LIGHT_DAY -> R.string.pb_why_light
}
