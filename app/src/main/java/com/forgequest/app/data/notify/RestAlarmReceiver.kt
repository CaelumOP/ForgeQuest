package com.forgequest.app.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forgequest.app.container

/** The rest timer's alarm went off. [RestAlert.fire] does nothing if the workout screen already alerted. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.container.restAlert.fire(intent.getStringExtra(EXTRA_NEXT))
    }

    companion object {
        const val EXTRA_NEXT = "next_exercise"
    }
}
