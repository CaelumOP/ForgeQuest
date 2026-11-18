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

    fun food(context: Context): String = raw(context, R.raw.food_gem_prompt)
}
