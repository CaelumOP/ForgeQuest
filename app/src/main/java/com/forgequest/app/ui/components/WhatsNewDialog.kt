package com.forgequest.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgequest.app.BuildConfig
import com.forgequest.app.ui.theme.Fit
import java.util.Locale

@Composable
fun WhatsNewGate() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whats_new_prefs", Context.MODE_PRIVATE) }
    val currentCode = BuildConfig.VERSION_CODE
    val savedCode = remember { prefs.getInt("last_seen_version_code", -1) }

    // If savedCode == -1, it's a brand-new install: record currentCode silently or show if you prefer.
    // Showing whenever savedCode != currentCode ensures everyone sees the patch notes once per version.
    var show by remember { mutableStateOf(savedCode != currentCode) }

    if (!show) return

    val c = Fit.colors
    val isArabic = Locale.getDefault().language == "ar"
    val title = if (isArabic) {
        "الجديد في الإصدار ${BuildConfig.VERSION_NAME}"
    } else {
        "What's New in v${BuildConfig.VERSION_NAME}"
    }

    val bullets = if (isArabic) {
        listOf(
            "هدف الجسم: خسارة الوزن أو المحافظة عليه أو زيادته، مع سعرات وبروتين مقترحة تناسب هدفك.",
            "زيادة أهدأ للأوزان: لا يرتفع الوزن إلا بعد جلستين ناجحتين، ويتوقف عند انخفاض الجاهزية.",
            "تقييم كل جولة بلمسة واحدة: سهل، مناسب، صعب، ألم.",
            "أيام المرض والسفر والإصابة والراحة لا تكسر سلسلتك، وتتوقف التذكيرات حين تبتعد.",
            "تعديلات أدق من المدرب على خطتك، وملخص قصير بالذكاء الاصطناعي بعد كل تمرين."
        )
    } else {
        listOf(
            "Cut, Maintain or Gain: pick your goal and get calorie and protein targets that fit it.",
            "Calmer progression: weight goes up only after two good sessions in a row, and holds when readiness is low.",
            "One-tap effort tags on each set: Easy, OK, Hard or Pain.",
            "Sick, travel, injured and rest days protect your streak, and reminders back off when you step away.",
            "Safer coach plan edits, and a short AI debrief after each workout."
        )
    }

    AlertDialog(
        onDismissRequest = {
            prefs.edit().putInt("last_seen_version_code", currentCode).apply()
            show = false
        },
        containerColor = c.card,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                bullets.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    prefs.edit().putInt("last_seen_version_code", currentCode).apply()
                    show = false
                }
            ) {
                Text(if (isArabic) "حسناً" else "Got it", color = c.teal, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}