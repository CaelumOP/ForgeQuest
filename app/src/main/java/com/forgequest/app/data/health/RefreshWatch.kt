package com.forgequest.app.data.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** What one read of Health Connect found. */
enum class RefreshResult { NEW_NIGHT, UPDATED, STILL_WAITING, FAILED }

/** The line the readiness card shows about the last refresh. */
enum class RefreshNote { UPDATED, NEW_NIGHT, WAITING, GAVE_UP, FAILED }

/** [before] and [after] are the epoch days on the card; [after] is null when the read failed. */
fun refreshResult(before: Long?, after: Long?, today: Long): RefreshResult = when {
    after == null -> RefreshResult.FAILED
    after < today -> RefreshResult.STILL_WAITING
    before != null && before < today -> RefreshResult.NEW_NIGHT
    else -> RefreshResult.UPDATED
}

/** Past 6 am and the card still shows yesterday: last night's sleep hasn't reached Health Connect yet. */
fun DayReadiness.showsYesterday(now: LocalDateTime = LocalDateTime.now()): Boolean =
    epochDay < now.toLocalDate().toEpochDay() && now.hour >= 6

/**
 * The readiness card's refresh button. Fitbit writes to Health Connect a few minutes after its own sync, so while the
 * card is still on yesterday this reads again every minute for a few minutes. It only runs while Home is on screen.
 */
class RefreshWatch(
    private val scope: CoroutineScope,
    private val tries: Int = 5,
    private val everyMs: Long = 60_000,
    private val refresh: suspend () -> RefreshResult?,
) {
    private val _note = MutableStateFlow<RefreshNote?>(null)
    val note: StateFlow<RefreshNote?> = _note
    private val _busy = MutableStateFlow(false)
    /** The refresh icon spins: a tap, or a re-read the card has said it is doing. */
    val busy: StateFlow<Boolean> = _busy

    private var loop: Job? = null
    private var clear: Job? = null

    fun tap() {
        loop?.cancel()
        scope.launch {
            // A read can take a blink; spin long enough to be seen.
            val r = spin { coroutineScope { val min = launch { delay(MIN_SPIN_MS) }; refresh().also { min.join() } } }
            show(r)
            if (r == RefreshResult.STILL_WAITING) watch()
        }
    }

    /**
     * Home came to the front after the read that gave [found]. Back from the Fitbit app the night may already be in
     * (say so), or land any minute (keep looking).
     */
    fun resume(found: RefreshResult?, waiting: Boolean) {
        if (found == RefreshResult.NEW_NIGHT) show(found)
        if (waiting && loop?.isActive != true) watch()
    }

    fun pause() {
        loop?.cancel()
    }

    private fun watch() {
        loop = scope.launch {
            repeat(tries) {
                delay(everyMs)
                val r = if (_note.value == RefreshNote.WAITING) spin { refresh() } else refresh()
                if (r == RefreshResult.STILL_WAITING) return@repeat
                // A quiet check that fails says nothing; the next tap will.
                if (r != RefreshResult.NEW_NIGHT && r != RefreshResult.UPDATED && _note.value != RefreshNote.WAITING) return@launch
                show(r)
                return@launch
            }
            if (_note.value == RefreshNote.WAITING) _note.value = RefreshNote.GAVE_UP
        }
    }

    private suspend fun <T> spin(block: suspend () -> T): T {
        _busy.value = true
        try { return block() } finally { _busy.value = false }
    }

    private fun show(r: RefreshResult?) {
        clear?.cancel()
        _note.value = when (r) {
            RefreshResult.NEW_NIGHT -> RefreshNote.NEW_NIGHT
            RefreshResult.UPDATED -> RefreshNote.UPDATED
            RefreshResult.STILL_WAITING -> RefreshNote.WAITING
            RefreshResult.FAILED -> RefreshNote.FAILED
            null -> null
        }
        if (r == RefreshResult.NEW_NIGHT || r == RefreshResult.UPDATED) clear = scope.launch { delay(NOTE_MS); _note.value = null }
    }

    private companion object {
        const val MIN_SPIN_MS = 700L
        const val NOTE_MS = 5_000L
    }
}
