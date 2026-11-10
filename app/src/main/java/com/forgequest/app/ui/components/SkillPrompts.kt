package com.forgequest.app.ui.components

import android.content.Context
import com.forgequest.app.R

/**
 * The instruction texts ("skills") a person pastes into their own Gem in the Gemini app, so the round trip works with
 * no API key: Gemini answers in a format the app can read, and the reply is shared or pasted back. Each ships in
 * English (res/raw) and Arabic (res/raw-ar); the plan one is copied by [GeminiPlanPills].
 */
object SkillPrompts {
    private fun raw(context: Context, id: Int): String = context.resources.openRawResource(id).bufferedReader().use { it.readText() }

    /** The meal-photo skill. The person's hand size goes in as the ruler; the line is dropped when it is not set. */
    fun food(context: Context, handLengthCm: Float, palmWidthCm: Float): String {
        val arabic = context.resources.configuration.locales[0].language == "ar"
        val hand = when {
            handLengthCm <= 0f -> ""
            arabic -> "يدي: الطول $handLengthCm سم، وعرض الكف $palmWidthCm سم. إذا ظهرت يدي في الصورة فاستخدمها مسطرة دقيقة.\n"
            else -> "MY HAND: length $handLengthCm cm, palm width $palmWidthCm cm. When my hand is in the photo, use it as an exact ruler.\n"
        }
        return raw(context, R.raw.food_gem_prompt).replace(Regex("MY HAND:[^\n]*\n"), hand)
    }

    /** The coach skill: how to read the app's data message and the reply format "Paste reply" accepts. */
    fun coach(context: Context): String = raw(context, R.raw.coach_skill)
}
