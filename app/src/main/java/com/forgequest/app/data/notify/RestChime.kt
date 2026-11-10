package com.forgequest.app.data.notify

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

/**
 * The short sound for "rest is over" while the workout screen is showing. A notification would cover the set the person
 * is about to log, so the app plays the tone itself: the phone's notification sound, on the alarm stream.
 */
class RestChime(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var playing: Ringtone? = null

    fun play() {
        main.post {
            runCatching {
                playing?.stop()
                val uri = Notifier.alertSound() ?: return@post
                val tone = RingtoneManager.getRingtone(context, uri) ?: return@post
                tone.audioAttributes = Notifier.ALARM_AUDIO
                tone.play()
                playing = tone
                // Some phones ship a long default tone; a rest ending needs a ding, not a song.
                main.postDelayed({ runCatching { tone.stop() }; if (playing === tone) playing = null }, MAX_MS)
            }
        }
    }

    private companion object {
        const val MAX_MS = 3_000L
    }
}
