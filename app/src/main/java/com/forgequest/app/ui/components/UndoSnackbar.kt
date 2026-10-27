package com.forgequest.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.forgequest.app.R
import com.forgequest.app.ui.LocalSnackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** One pending "Removed X. Undo" offer. The screen's ViewModel owns it; [UndoSnackbarEffect] shows it. */
class UndoState(private val scope: CoroutineScope) {
    val pending = MutableStateFlow<Pair<String, suspend () -> Unit>?>(null)

    /** Offer an undo: [text] is the snackbar message, [restore] puts the item back. */
    fun offer(text: String, restore: suspend () -> Unit) { pending.value = text to restore }

    fun run() { scope.launch { pending.value?.second?.invoke(); pending.value = null } }
}

/** Shows the snackbar for [undo]'s pending offer and runs it when the user taps Undo. */
@Composable
fun UndoSnackbarEffect(undo: UndoState, duration: SnackbarDuration = SnackbarDuration.Short) {
    val snackbar = LocalSnackbar.current
    val undoLabel = stringResource(R.string.action_undo)
    val pending by undo.pending.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { (text, _) ->
            val result = snackbar.showSnackbar(text, actionLabel = undoLabel, duration = duration)
            if (result == SnackbarResult.ActionPerformed) undo.run() else if (undo.pending.value?.first == text) undo.pending.value = null
        }
    }
}
