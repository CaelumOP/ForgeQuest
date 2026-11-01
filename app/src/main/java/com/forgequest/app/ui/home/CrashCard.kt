package com.forgequest.app.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.crash.CrashReport
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * After a crash, once: "ForgeQuest closed unexpectedly last time. Send a crash report?" Send opens the share sheet with
 * the report text (no recipient filled in); either button deletes the saved report.
 */
@Composable
fun CrashCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { CrashReport.pending(context) } }
    val text = report ?: return
    val c = Fit.colors
    val done = { report = null; CrashReport.clear(context) }
    FitCard(modifier = modifier, padding = PaddingValues(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.BugReport, c.coral, c.coralTrack, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.crash_prompt), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary,
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                )
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = done) { Text(stringResource(R.string.crash_dismiss), color = c.textSecondary, style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "ForgeQuest crash report")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(Intent.createChooser(send, null)) }
                    done()
                }) { Text(stringResource(R.string.crash_send), color = c.coral, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
