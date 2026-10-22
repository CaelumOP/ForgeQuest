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
            "استشفاء عضلي واقعي: يُحتسب التعب الآن بناءً على عدد الجولات والعدات الفعلية وشدة التمرين بدلاً من 48 ساعة ثابتة لمجرد عدة واحدة.",
            "محرك استبدال التمارين الذكي: يقترح بدائل متطابقة ميكانيكياً وحركياً حسب المعدات المتوفرة لديك.",
            "مدرب ذكي واعي بالمعدات: يسألك المدرب عن معداتك أولاً ويصمم جدولك بناءً عليها، مع كشف تلقائي لثبات الأوزان والحاجة لأسبوع تخفيف الحمل (Deload).",
            "معادلات تغذية دقيقة: دعم معادلة Katch-McArdle لنسبة الدهون، وحدود آمنة للسعرات والبروتين والدهون الأساسية.",
            "أزرار سريعة لتقييم الجهد (RPE / RIR) داخل التمرين وتطوير تلقائي للأوزان والعدات."
        )
    } else {
        listOf(
            "Realistic muscle recovery: fatigue and recovery hours now scale with your actual sets, reps, load, and effort instead of jumping to 48h after 1 rep.",
            "Biomechanical exercise swapper: suggests movement-pattern and joint-angle matches filtered by your available equipment.",
            "Equipment-aware AI Coach: checks your available equipment before building plans and automatically detects lift plateaus & deload weeks.",
            "Upgraded nutrition math: adds optional Body Fat % (Katch-McArdle BMR) and enforces safe calorie, protein, and essential fat floors.",
            "One-tap RPE / RIR effort tags on set notes with automatic double-progression overload."
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