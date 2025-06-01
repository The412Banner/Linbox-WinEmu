package org.github.ewt45.winemulator.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.MainEmuApplication
import org.github.ewt45.winemulator.Utils
import org.github.ewt45.winemulator.Utils.Permissions.isGranted
import org.github.ewt45.winemulator.permissions.RequiredPermissions

data class PrepareUiState(
    val loading: Boolean = true,
    val unGrantedPermissions: List<RequiredPermissions> = listOf(),
    val ignorePermissions:Boolean = false,
    val noRootfs: Boolean = false,
    val forceNoRootfs: Boolean = false,
    val shouldRestart: Boolean = false,
) {
    /** 准备完成。若返回true则应离开prepareScreen 进入主界面 */
    fun prepareFinished(): Boolean {
        return !loading && unGrantedPermissions.isEmpty() && !noRootfs && !forceNoRootfs
    }
}

class PrepareViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PrepareUiState(loading = true))
    val uiState: StateFlow<PrepareUiState> = _uiState.asStateFlow()

//    val isNoRootfs = mutableStateOf(true)
//
    fun setNoRootfs(noRootfs: Boolean) {
        _uiState.update { it.copy(noRootfs = noRootfs) }
    }

    /** 进入preparescreen之后执行此函数 检查是否有必要显示 */
    suspend fun updateState() = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(loading = true) }
        val unGrantedList = RequiredPermissions.entries.mapNotNull { it.takeUnless { isGranted(MainEmuApplication.i, it.permission) } }
        val noRootfs = Utils.Rootfs.haveNoRootfs()
        _uiState.update { it.copy(loading = false, unGrantedPermissions = unGrantedList, noRootfs = noRootfs) }

    }

    /** 用户授权某个权限后，修改未授予权限列表 */
    fun onGrantedPermission(permission: RequiredPermissions) {
        _uiState.update { it.copy(unGrantedPermissions = it.unGrantedPermissions.minus(permission)) }
    }

}