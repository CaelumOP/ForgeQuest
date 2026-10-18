package com.forgequest.app.data.bridge

import android.content.Context
import com.forgequest.app.R
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.drive.DriveSync
import com.forgequest.app.data.health.HealthRepository
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.suspendRunCatching
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The owner-only link with a Gemini app automation: twice a day the app writes everything it knows into the
 * "ForgeQuest data" Doc, the automation analyses it with a big model and writes "ForgeQuest from Gemini", and the app
 * reads that back. The same text can be copied and pasted by hand when Drive isn't set up.
 */
class GeminiBridge(
    private val context: Context,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val coach: CoachRepository,
    private val health: HealthRepository,
    private val drive: DriveSync,
) {
    /** Everything Gemini needs, as one readable document. */
    suspend fun dataText(): String {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val sb = StringBuilder()
        sb.appendLine("ForgeQuest data, updated ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))} (${zone.id})")
        sb.appendLine("Written by the ForgeQuest app for the owner's Gemini automation. Reply in the \"${BridgeFormat.REPLY_DOC}\" document using the exact format from your instructions.")
        sb.appendLine()
        sb.appendLine("## ATHLETE")
        sb.appendLine(coach.progressSummary().trim())
        sb.appendLine()
        sb.appendLine("## READINESS TODAY")
        sb.appendLine(health.coachLine()?.removePrefix("- ")?.trim() ?: "Fitbit readiness is not connected or has no data yet.")
        sb.appendLine()
        val days = db.readiness().all().takeLast(30)
        sb.appendLine("## READINESS HISTORY (one line per day, newest last)")
        if (days.isEmpty()) sb.appendLine("No saved days yet.")
        else {
            sb.appendLine("date | recovery % | asleep | sleep performance % | HRV ms | resting HR | strain /21 | felt great")
            days.forEach { d ->
                sb.appendLine(
                    "${LocalDate.ofEpochDay(d.epochDay)} | ${d.recovery} | ${d.sleepMin / 60}h${"%02d".format(d.sleepMin % 60)} | ${d.sleepPerf} | " +
                        "${d.hrv.toInt()} | ${d.rhr.toInt()} | ${"%.1f".format(Locale.US, d.strain)} | ${if (d.feltGreat) "yes" else "-"}"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("## PLAN FORMAT (only needed if you change the plan)")
        sb.appendLine(coach.planSchemaInstructions())
        return sb.toString()
    }

    /** Writes the data Doc and reads a newer reply if there is one. Returns null when there was nothing new to read. */
    suspend fun sync(token: String): BridgeReply? {
        val s = settings.current()
        val id = drive.upsertTextDoc(token, BridgeFormat.DATA_DOC, dataText(), s.bridgeDataId.ifBlank { null })
        settings.update { it.copy(bridgeDataId = id, bridgeLastWrite = System.currentTimeMillis()) }
        val doc = drive.newestDoc(token, BridgeFormat.REPLY_DOC, s.bridgeLastReplyModified, context.getString(R.string.bridge_reply_placeholder)) ?: return null
        // The placeholder the app wrote itself isn't a reply.
        if (!doc.first.contains(BridgeFormat.HEADER)) { settings.update { it.copy(bridgeLastReplyModified = doc.second) }; return null }
        val reply = BridgeFormat.parse(doc.first)
        apply(reply)
        settings.update { it.copy(bridgeLastReplyModified = doc.second) }
        return reply
    }

    /** Applies a reply: today's line for Home, the analysis as a coach message, notes into memory, a plan as a card to review. */
    suspend fun apply(reply: BridgeReply) {
        val now = System.currentTimeMillis()
        settings.update {
            val merged = (it.coachNotes.lines().map(String::trim).filter(String::isNotBlank) + reply.notes)
                .distinctBy { n -> n.lowercase() }.takeLast(25).joinToString("\n")
            it.copy(bridgeToday = reply.today, bridgeIssues = reply.issues.joinToString(", "), bridgeLastReadAt = now, coachNotes = merged)
        }
        val chat = db.chat()
        if (reply.week.isNotBlank()) {
            chat.insert(ChatEntity(role = "coach", text = context.getString(R.string.bridge_week_title) + "\n\n" + reply.week, kind = "gemini", createdAt = now))
        }
        reply.planJson?.let { json ->
            suspendRunCatching { coach.parsePlanText("```json\n$json\n```") }.getOrNull()?.let { plan ->
                chat.insert(
                    ChatEntity(role = "coach", text = context.getString(R.string.bridge_plan_card), planJson = PlanJson.encode(plan), createdAt = now + 1)
                )
            } ?: settings.update { it.copy(bridgeIssues = (reply.issues + "PLAN JSON").distinct().joinToString(", ")) }
        }
    }

    /** For the paste fallback. */
    suspend fun applyText(text: String): BridgeReply = BridgeFormat.parse(text).also { apply(it) }
}
