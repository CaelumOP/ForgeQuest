package com.forgequest.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.forgequest.app.data.bridge.MealFormat
import com.forgequest.app.data.bridge.MealInbox
import com.forgequest.app.ui.components.toast
import kotlinx.coroutines.launch

/**
 * "Share to ForgeQuest" for a meal estimate from the Gemini Gem (or any AI app using the scan format). Puts it in the
 * Food tab inbox, skips one that was already logged, and opens the review sheet in the main app. Has no screen of its own.
 */
class ShareMealActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        lifecycleScope.launch {
            handle(text)
            finish()
        }
    }

    private suspend fun handle(text: String?) {
        val meal = text?.let { MealFormat.parseOne(it) } ?: return toast(getString(R.string.meals_share_wrong))
        val inbox = container.meals
        if (inbox.offer(meal) == MealInbox.Offer.ALREADY_LOGGED) return toast(getString(R.string.meals_already_logged))
        inbox.review.value = inbox.stored(meal)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
