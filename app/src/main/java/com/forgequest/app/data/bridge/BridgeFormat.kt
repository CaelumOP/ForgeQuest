package com.forgequest.app.data.bridge

/**
 * The reply the owner's Gemini automation writes back ("ForgeQuest from Gemini"). The format is deliberately plain so
 * a big model keeps to it:
 *
 *     FORGEQUEST-REPLY v1
 *     ## TODAY
 *     one to three sentences for today
 *     ## WEEK
 *     - analysis bullets
 *     ## COACH NOTES
 *     - facts the in-app coach should remember
 *     ## PLAN
 *     NO CHANGE   (or a ```json block in the app's plan shape)
 *
 * Anything missing is reported by name instead of silently ignored.
 */
data class BridgeReply(
    val today: String,
    val week: String,
    val notes: List<String>,
    /** Plan JSON text when Gemini proposes a new week, null for NO CHANGE or no section. */
    val planJson: String?,
    /** Section names that were missing or empty, for the status line in Settings. */
    val issues: List<String>,
) {
    val ok get() = issues.isEmpty()
}

object BridgeFormat {
    const val HEADER = "FORGEQUEST-REPLY"
    const val DATA_DOC = "ForgeQuest data"
    const val REPLY_DOC = "ForgeQuest from Gemini"
    private val SECTIONS = listOf("TODAY", "WEEK", "COACH NOTES", "PLAN")

    fun parse(raw: String): BridgeReply {
        // Docs exports can carry a BOM, smart quotes and \r; normalise before looking for markers.
        val text = raw.replace("﻿", "").replace("\r", "").replace('“', '"').replace('”', '"')
        val start = text.indexOf(HEADER)
        val body = if (start >= 0) text.substring(start) else text
        val sections = mutableMapOf<String, String>()
        var current: String? = null
        val buf = StringBuilder()
        fun flush() { current?.let { sections[it] = buf.toString().trim() }; buf.clear() }
        for (line in body.lines()) {
            val heading = Regex("^#{1,3}\\s*(.+?)\\s*:?\\s*$").matchEntire(line.trim())?.groupValues?.get(1)?.uppercase()
            if (heading != null && heading in SECTIONS) { flush(); current = heading; continue }
            if (current != null) buf.appendLine(line)
        }
        flush()

        val issues = mutableListOf<String>()
        if (start < 0) issues += "header"
        val today = sections["TODAY"].orEmpty().also { if (it.isBlank()) issues += "TODAY" }
        val week = sections["WEEK"].orEmpty().also { if (it.isBlank()) issues += "WEEK" }
        val notes = sections["COACH NOTES"].orEmpty().lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        val planSection = sections["PLAN"]
        val planJson = when {
            planSection == null -> { issues += "PLAN"; null }
            planSection.contains("NO CHANGE", ignoreCase = true) && !planSection.contains("{") -> null
            else -> extractJson(planSection).also { if (it == null) issues += "PLAN JSON" }
        }
        return BridgeReply(today, week, notes, planJson, issues)
    }

    /** The first {...} block, from a ```json fence or bare. */
    internal fun extractJson(s: String): String? {
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(s)?.groupValues?.get(1)
        if (fenced != null) return fenced
        val open = s.indexOf('{'); val close = s.lastIndexOf('}')
        return if (open >= 0 && close > open) s.substring(open, close + 1) else null
    }
}
