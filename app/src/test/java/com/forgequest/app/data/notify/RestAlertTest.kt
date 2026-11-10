package com.forgequest.app.data.notify

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** The rest-over alert: scheduled on start, moved on "+30 s", cancelled on skip, and never delivered twice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RestAlertTest {
    private class FakeScheduler : RestAlarmScheduler {
        var at: Long? = null
        var next: String? = null
        override fun schedule(atMillis: Long, nextExercise: String?) { at = atMillis; next = nextExercise }
        override fun cancel() { at = null }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = context.getSharedPreferences("rest_alert_test", Context.MODE_PRIVATE)
    private val scheduler = FakeScheduler()
    private val alerts = mutableListOf<Pair<String?, Boolean>>()
    private fun newAlert() = RestAlert(store, scheduler) { next, onScreen -> alerts += next to onScreen }

    @Before fun clear() {
        store.edit().clear().commit()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test fun start_schedules_the_alarm_for_the_end_of_the_rest() {
        newAlert().start(90_000L, "Squat")
        assertEquals(90_000L, scheduler.at)
        assertEquals("Squat", scheduler.next)
    }

    @Test fun adding_time_moves_the_alarm() {
        val alert = newAlert()
        alert.start(90_000L, "Squat")
        alert.start(120_000L, "Squat")
        assertEquals(120_000L, scheduler.at)
    }

    @Test fun skip_cancels_the_alarm_and_a_late_alarm_stays_quiet() {
        val alert = newAlert()
        alert.start(90_000L, "Squat")
        alert.cancel()
        assertNull(scheduler.at)
        assertFalse(alert.fire("Squat"))
        assertTrue(alerts.isEmpty())
    }

    @Test fun the_screen_clock_and_the_alarm_alert_only_once() {
        val alert = newAlert()
        alert.screenVisible = true
        alert.start(90_000L, "Squat")
        assertTrue(alert.fire("Squat"))   // the workout screen's clock
        assertFalse(alert.fire("Squat"))  // the alarm, a moment later
        assertEquals(listOf<Pair<String?, Boolean>>("Squat" to true), alerts)
        assertNull("the alarm is cancelled once the screen has alerted", scheduler.at)
    }

    @Test fun the_alarm_still_alerts_after_the_app_was_closed_mid_rest() {
        newAlert().start(90_000L, "Squat")
        val afterRestart = newAlert()   // a new process: the saved end time, nothing in memory
        assertTrue(afterRestart.fire("Squat"))
        assertEquals(listOf<Pair<String?, Boolean>>("Squat" to false), alerts)
    }

    @Test fun nothing_alerts_when_no_rest_is_running() {
        assertFalse(newAlert().fire(null))
        assertTrue(alerts.isEmpty())
    }

    // ------------------------------------------------------------ The real AlarmManager

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Test fun exact_alarm_is_set_when_allowed_and_replaced_not_stacked() {
        val alarms = RestAlarms(context, store)
        alarms.schedule(90_000L, "Squat")
        alarms.schedule(120_000L, "Squat")
        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(120_000L, scheduled.single().triggerAtMs)
    }

    @Test fun falls_back_to_an_inexact_alarm_when_exact_is_refused() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val alarms = RestAlarms(context, store)
        assertFalse(alarms.exactAllowed())
        alarms.schedule(90_000L, null)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertTrue("offered until answered", alarms.shouldOfferExact())
        alarms.offerAnswered()
        assertFalse(alarms.shouldOfferExact())
    }

    @Test fun cancel_removes_the_alarm() {
        val alarms = RestAlarms(context, store)
        alarms.schedule(90_000L, "Squat")
        alarms.cancel()
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
