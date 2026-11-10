package com.forgequest.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.ui.LocalSnackbar
import kotlinx.coroutines.launch

/**
 * The Gemini-app plan round trip: copy the plan prompt, then paste Gemini's reply. [onPaste] opens the plan editor's paste
 * step. The app copies res/raw/plan_prompt.txt, mirrored in docs/prompts/plan-from-youtube.md.
 */
@Composable
fun GeminiPlanPills(onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = rememberTextClipboard()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.plan_prompt_copied)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TonalPill(stringResource(R.string.plan_gemini_copy), onClick = {
            val prompt = context.resources.openRawResource(R.raw.plan_prompt).bufferedReader().use { it.readText() }
            clipboard.copy(prompt)
            if (android.os.Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar(copied) }
        }, modifier = Modifier.weight(1f), icon = Icons.Rounded.ContentCopy)
        TonalPill(stringResource(R.string.plan_gemini_paste), onClick = onPaste, modifier = Modifier.weight(1f), icon = Icons.Rounded.ContentPaste)
    }
}
