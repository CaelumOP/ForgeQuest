package com.forgequest.app.data.bridge

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Owner-only round trip: send the data and a question to the Gemini app, paste its reply back. */
object AskGemini {
    fun prompt(data: String, question: String, arabic: Boolean): String {
        val language = if (arabic) "formal Modern Standard Arabic (الفصحى), not dialect" else "English"
        val ask = if (question.isBlank()) {
            "My question is blank: give today's advice and a short review of my week."
        } else {
            "Answer my question below."
        }
        return """
            You are the head coach of one athlete (me), working through the ForgeQuest fitness app. My data is below the question.
            Do not open, create or edit any Google Doc. Use only the text in this message and never invent numbers, workouts or injuries.

            STEP 1. First answer in the chat: $ask Plain, warm, short, like a good coach texting. No hype, no emojis. Write in $language.

            STEP 2. Then, in one single code block, give your reply in the exact FORGEQUEST-REPLY v1 format, with nothing before the first line and nothing after the last section:
            FORGEQUEST-REPLY v1
            ## TODAY
            One to three sentences: what to do today and why.
            ## WEEK
            Three to eight bullets starting with "- ".
            ## COACH NOTES
            Zero to five bullets starting with "- " (write "- none" if nothing new).
            ## PLAN
            Either the words NO CHANGE, or the complete new weekly plan as bare JSON in the PLAN FORMAT from the data (no ``` fence: it is already inside the code block). Write NO CHANGE unless there is a clear reason.
        """.trimIndent() +
            // Appended after trimIndent: the data's unindented lines would otherwise stop the template being trimmed.
            "\n\nMY QUESTION:\n${question.trim()}\n\nMY DATA:\n$data"
    }

    /** Copies [text], then opens the Gemini app with it. False when the app is missing (the web page opens instead). */
    fun send(context: Context, text: String): Boolean {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("ForgeQuest", text))
        return try {
            context.startActivity(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    .setPackage("com.google.android.apps.bard").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            false
        }
    }
}
