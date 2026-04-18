package org.github.ewt45.winemulator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class ConfirmDialogState {
    val text = mutableStateOf("")
    val onConfirm: MutableState<(suspend () -> Unit)?> = mutableStateOf(null)
    val show = mutableStateOf(false)
    val confirmNeeded = mutableStateOf(true)

    private fun updateValuesAndShow(
        text: String = "",
        confirmNeeded: Boolean = true,
        onConfirm: (suspend () -> Unit)? = null
    ) {
        this.text.value = text
        this.onConfirm.value = onConfirm
        this.confirmNeeded.value = confirmNeeded
        this.show.value = true
    }

    /** Shows a dialog; if the user taps OK, executes [onConfirm] and closes. Exceptions are shown as an error message. */
    fun showConfirm(text: String, onConfirm: (suspend () -> Unit)? = null) {
        updateValuesAndShow(text, true) {
            if (onConfirm == null) return@updateValuesAndShow
            runCatching { onConfirm() }.exceptionOrNull()?.let { e ->
                // Show a concise error message rather than the full stack trace
                val errorMsg = e.message ?: e.javaClass.simpleName
                updateValuesAndShow("Operation failed: $errorMsg")
            }
        }
    }

    /**
     * Like [showConfirm] but no confirmation needed — executes immediately after showing.
     */
    fun showBlock(text: String = "", action: suspend () -> Unit) {
        updateValuesAndShow(text, false) {
            runCatching { action() }.exceptionOrNull()?.let { e ->
                // Show a concise error message rather than the full stack trace
                val errorMsg = e.message ?: e.javaClass.simpleName
                updateValuesAndShow("Operation failed: $errorMsg")
            }
        }
    }

}

@Composable
fun rememberConfirmDialogState(text: String = "", onConfirm: (suspend () -> Unit)? = null): ConfirmDialogState {
    val state = ConfirmDialogState()
    state.text.value = text
    state.onConfirm.value = onConfirm
    return remember { state }
}

@Composable
fun ConfirmDialog(state: ConfirmDialogState) {
    val text by remember { state.text }
    val onConfirm by remember { state.onConfirm }
    val (show, setShow) = remember { state.show }
    val confirmNeeded by remember { state.confirmNeeded }
    ConfirmDialog(show, setShow, text, confirmNeeded, onConfirm = onConfirm)
}

/** When [isBlock] is true, [onConfirm] must not be null. */
@Composable
private fun ConfirmDialog(
    show: Boolean,
    setShow: (Boolean) -> Unit,
    text: String,
    confirmNeeded: Boolean,
    onConfirm: (suspend () -> Unit)?
) {
    /*
    After the confirm button is tapped, isBlocking is set to true, triggering the LaunchedEffect to run the action,
    which then hides the dialog via setShow.
     */

    val scope = rememberCoroutineScope()
    // isBlocking: if true, execute onConfirm immediately; otherwise wait for user confirmation
    var isBlocking by remember(show, confirmNeeded) { mutableStateOf(show && !confirmNeeded) }


    // When this is a blocking dialog, run the action after showing, then hide or display the error
    LaunchedEffect(isBlocking) {
        if (isBlocking) {
            if (onConfirm != null) onConfirm()
            setShow(false)
        }
    }

    // Confirm button hidden while blocking
    val confirmButton: @Composable() (() -> Unit) = if (isBlocking) {
        { }
    } else {
        { TextButton({ isBlocking = true }) { Text(stringResource(android.R.string.ok)) } }
    }

    // Cancel button hidden when blocking or when there is no confirm action
    val dismissButton: @Composable() (() -> Unit)? = if (onConfirm == null || isBlocking) {
        null
    } else {
        { TextButton({ setShow(false) }) { Text(stringResource(android.R.string.cancel)) } }
    }

    val dialogContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
            if (isBlocking) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }

    if (show) {
        AlertDialog(
            {},
            text = dialogContent,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}