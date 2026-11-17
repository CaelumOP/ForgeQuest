package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import kotlinx.serialization.Serializable

@Serializable
enum class SetType { WORKING, WARMUP, DROPSET, FAILURE, RESTPAUSE }

/** Default WORKING also represents legacy rows, whose block exclusions must survive restore. */
fun countsForProgression(set: SetEntity): Boolean = when (set.setType) {
    SetType.WORKING.name -> !WorkoutScore.isExtra(set.block)
    SetType.FAILURE.name -> true
    else -> false
}

fun countsForScore(set: SetEntity): Boolean = countsForProgression(set)

/** A set the user actually trained, for "N sets" labels: drop/rest-pause/failure count, warm-ups and cool-downs don't. */
fun countsAsWorkingSet(set: SetEntity): Boolean = set.setType != SetType.WARMUP.name && !WorkoutScore.isExtra(set.block)
fun countsForPR(set: SetEntity): Boolean = !set.autoTicked && countsForProgression(set)
