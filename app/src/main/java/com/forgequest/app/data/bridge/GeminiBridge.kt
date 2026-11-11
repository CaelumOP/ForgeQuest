package com.forgequest.app.data.bridge

import android.content.Context
import com.forgequest.app.R
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.health.HealthRepository
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.suspendRunCatching
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The hand-off with the Gemini app: [dataText] is what the person sends along with a question, and a pasted reply
 * (plan or weekly analysis) is applied by [applyText].
 */
class GeminiBridge(
    private val context: Context,
    private val db: AppDatabase,
    private val coach: CoachRepository,
    private val health: HealthRepository,
) {
    /** Everything Gemini needs, as one readable document. */
    suspend fun dataText(): String {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val sb = StringBuilder()
        sb.appendLine("ForgeQuest data, updated ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))} (${zone.id})")
        sb.appendLine("Copied from the ForgeQuest app. Reply using the exact format from your instructions; the person will paste your reply back into the app.")
        sb.appendLine()
        sb.appendLine("## ATHLETE")
        sb.appendLine(coach.prompts.progressSummary().trim())
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
                    "${LocalDate.ofEpochDay(d.epochDay)} | ${d.recovery} | ${d.sleepMin / 60}h${"%02d".format(Locale.US, d.sleepMin % 60)} | ${d.sleepPerf} | " +
                        "${d.hrv.toInt()} | ${d.rhr.toInt()} | ${"%.1f".format(Locale.US, d.strain)} | ${if (d.feltGreat) "yes" else "-"}"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("## PLAN FORMAT (only needed if you change the plan)")
        sb.appendLine(coach.prompts.planSchemaInstructions())
        return sb.toString()
    }

    /** Applies a reply: the analysis as a coach message, a plan as a card to review. Coach notes are only edited by the person. */
    suspend fun apply(reply: BridgeReply) {
        val now = System.currentTimeMillis()
        val chat = db.chat()
        if (reply.week.isNotBlank()) {
            chat.insert(ChatEntity(role = "coach", text = context.getString(R.string.bridge_week_title) + "\n\n" + reply.week, kind = "gemini", createdAt = now))
        }
        reply.planJson?.let { json ->
            // The reply is already in the app's plan shape; only ask the AI to convert it when it doesn't parse.
            (PlanJson.parsePasted(json) ?: suspendRunCatching { coach.parsePlanText("```json\n$json\n```") }.getOrNull())?.let { plan ->
                chat.insert(
                    ChatEntity(role = "coach", text = context.getString(R.string.bridge_plan_card), planJson = PlanJson.encode(plan), createdAt = now + 1)
                )
            }
        }
    }

    /** For the paste fallback. */
    suspend fun applyText(text: String): BridgeReply = BridgeFormat.parse(text).also { apply(it) }
}
