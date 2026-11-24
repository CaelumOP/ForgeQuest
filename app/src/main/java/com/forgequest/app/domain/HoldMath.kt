package com.forgequest.app.domain

/** Reaction-time grace for timed holds: stopping within [GRACE_SEC] seconds after the target saves the target. */
object HoldMath {
    const val GRACE_SEC = 3

    fun recorded(held: Int, target: Int): Int =
        if (target > 0 && held in target..target + GRACE_SEC) target else held
}
