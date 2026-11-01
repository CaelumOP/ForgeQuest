package com.forgequest.app.ui.components

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Plain-text copy and paste over the suspend-based [LocalClipboard]. */
class TextClipboard(private val clipboard: androidx.compose.ui.platform.Clipboard, private val scope: CoroutineScope) {
    fun copy(text: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
    }

    suspend fun read(): String? =
        clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    fun readThen(block: (String?) -> Unit) {
        scope.launch { block(read()) }
    }
}

@Composable
fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) { TextClipboard(clipboard, scope) }
}
