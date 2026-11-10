package com.forgequest.app.domain

/** Up to [n] freshest muscles (ready now) and up to [n] still recovering (soonest ready first). Pure, for the AI-off Coach tab. */
fun recoverySplit(map: Map<Muscle, MuscleRecovery>, n: Int = 3): Pair<List<Muscle>, List<Muscle>> {
    val fresh = map.filter { it.value.hoursUntilReady <= 0 }.entries.sortedBy { it.value.fatigue }.take(n).map { it.key }
    val recovering = map.filter { it.value.hoursUntilReady > 0 }.entries.sortedBy { it.value.hoursUntilReady }.take(n).map { it.key }
    return fresh to recovering
}
