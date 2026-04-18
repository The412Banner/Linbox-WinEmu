package org.github.ewt45.winemulator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.MainEmuApplication
import org.github.ewt45.winemulator.Utils
import org.github.ewt45.winemulator.Utils.Permissions.isGranted
import org.github.ewt45.winemulator.Utils.Ui.editDateStore
import org.github.ewt45.winemulator.Utils.Ui.editDateStoreAsync
import org.github.ewt45.winemulator.permissions.RequiredPermissions

data class PrepareUiState(
    val loading: Boolean = true,
    val unGrantedPermissions: List<RequiredPermissions> = listOf(),
    val skipPermissions: Boolean = false,
    val noRootfs: Boolean = false,
    val forceNoRootfs: Boolean = false,
    val shouldRestart: Boolean = false,
) {
    /** Preparation is done. When true, the app should leave PrepareScreen and enter the main UI. */
    val isPrepareFinished:Boolean
        get() = !loading
                && (skipPermissions || unGrantedPermissions.isEmpty())
                && !noRootfs && !forceNoRootfs
}

class PrepareViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PrepareUiState(loading = true))
    val uiState: StateFlow<PrepareUiState> = _uiState.asStateFlow()

    /** Call after entering PrepareScreen to check whether it should be shown. */
    suspend fun updateState() = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(loading = true) }
        val unGrantedList = RequiredPermissions.getUnGrantedList()
        _uiState.update {
            it.copy(
                loading = false,
                unGrantedPermissions = unGrantedList,
                skipPermissions = Consts.Pref.Local.skip_permissions.get(),
                noRootfs = Utils.Rootfs.haveNoRootfs()
            )
        }
    }

    /** Called when the user grants a permission; removes it from the ungranted list. */
    fun onGrantedPermission(permission: RequiredPermissions) {
        _uiState.update { it.copy(unGrantedPermissions = it.unGrantedPermissions.minus(permission)) }
    }

    /** Called when the user skips the permission request. */
    fun onSkipPermissions() {
        editDateStoreAsync(Consts.Pref.Local.skip_permissions.key, true)
        _uiState.update { it.copy(skipPermissions = true) }
    }

    /** Call when adding a rootfs. Sets [PrepareUiState.forceNoRootfs] to true to navigate to PrepareScreen. */
    fun setForceNoRootfs() {
        _uiState.update { it.copy(forceNoRootfs = true) }
    }

    /** Cancels new-container creation; sets [PrepareUiState.forceNoRootfs] back to false. */
    fun onCancelForceNoRootfs() {
        _uiState.update { it.copy(forceNoRootfs = false) }
    }

    /** Called after a rootfs is extracted automatically; updates state accordingly. */
    fun onRootfsExtracted(rootfsName: String) {
        _uiState.update { it.copy(forceNoRootfs = false, noRootfs = false) }
    }
}