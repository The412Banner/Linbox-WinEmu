package org.github.ewt45.winemulator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.FuncOnChangeAction
import org.github.ewt45.winemulator.ui.Destination

/** UI state for [MainViewModel]. */
data class MainUiState(
    /** dialog type */
    val dialogType: DialogType = DialogType.NONE,
    /** dialog message */
    val msg: String = "",

)

// TODO: add data to BLOCK and CONFIRM, convert to sealed uiState (see nowinandroid pattern)
sealed interface DialogType {
    /** Blocking — user cannot dismiss; closes automatically when the action completes. */
    data object BLOCK:DialogType
    /** Confirmation — shows a message with OK / Cancel buttons. */
    data object CONFIRM:DialogType
    /** No dialog shown. */
    object NONE:DialogType
}

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _navigateToEvent = MutableSharedFlow<Destination>()
    val navigateToEvent: SharedFlow<Destination> = _navigateToEvent.asSharedFlow()

    // Usage: assign CompletableDeferred() at the start, call .await() to wait; call .complete() at the end to resume .await()
    private var dialogDeferred: CompletableDeferred<Result<Boolean>>? = null


    suspend fun navigateToPrepareScreen() {
        _navigateToEvent.emit(Destination.Prepare)
    }

    /** Navigates to the given [destination]. */
    fun navigateTo(destination: Destination) {
        viewModelScope.launch {
            _navigateToEvent.emit(destination)
        }
    }

    /**
     * Shows a blocking dialog, executes [action], then closes the dialog automatically.
     * @param action the operation to run
     * @return Result.success if [action] succeeded, Result.failure otherwise
     */
    suspend fun <T> showBlockDialog(msg: String = "Loading, please wait…", action: (suspend () -> T)):Result<T> {
        // Update state to show the dialog
        _uiState.update { it.copy(dialogType = DialogType.BLOCK, msg = msg) }
        // Execute the action
        val result = kotlin.runCatching { action() }
        _uiState.update { it.copy(dialogType = DialogType.NONE) }
        return result
    }

    /**
     * Shows a confirmation dialog and suspends until the user taps OK or Cancel.
     * Returns Result(true) for OK, Result(false) for Cancel.
     */
    suspend fun showConfirmDialog(msg: String = "Are you sure?"):Result<Boolean>  {
        dialogDeferred = CompletableDeferred()
        // Ensure state update runs on the main thread to avoid a race condition on first launch
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(dialogType = DialogType.CONFIRM, msg = msg)  }
        }
        val result = dialogDeferred!!.await()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(dialogType = DialogType.NONE) }
        }
        return result
    }

    /**
     * Closes the confirmation dialog.
     * @param confirm true if the user tapped OK, false if they tapped Cancel
     */
    fun closeConfirmDialog(confirm:Boolean = true) {
        dialogDeferred?.complete(Result.success(confirm))
    }

    fun showErrorDialog(msg:String, isFatal:Boolean = false) {
        TODO()
    }

    /**
     * Shows a blocking dialog via [showBlockDialog] and runs [action].
     * [action] returns a String; a non-empty string indicates failure and is shown via [showConfirmDialog].
     */
    suspend fun showBlockDialogWithErrorConfirm(msg: String,  action: (suspend () -> String)) {
        val result = showBlockDialog(msg, action)
        val str = if (result.getOrNull() != null) result.getOrNull()!! else result.exceptionOrNull()!!.stackTraceToString()
        if (str.isNotEmpty()) {
            showConfirmDialog(str)
        }
    }

}