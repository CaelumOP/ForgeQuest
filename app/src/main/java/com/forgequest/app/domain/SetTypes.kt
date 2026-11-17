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
fun countsForPR(set: SetEntity): Boolean = countsForProgression(set)
