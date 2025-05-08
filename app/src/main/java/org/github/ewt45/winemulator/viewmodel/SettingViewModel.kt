package org.github.ewt45.winemulator.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.termux.x11.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Utils.Ui.editDateStore
import org.github.ewt45.winemulator.Utils.Ui.stateInSimple
import org.github.ewt45.winemulator.dataStore
import org.github.ewt45.winemulator.Consts.Pref
import org.github.ewt45.winemulator.Consts.Pref.general_resolution
import org.github.ewt45.winemulator.Consts.Pref.proot_bool_options
import org.github.ewt45.winemulator.Consts.Pref.proot_startup_cmd
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.RateLimiter
import org.github.ewt45.winemulator.Utils

private val TAG = "SettingViewModel"

data class PrefProot(
    /** 只会出现一次且没有附加参数的选项。有全名就尽量使用全名 */
    val boolOptions: Set<String> = Pref.proot_bool_options.default,
    val startupCmd: String = Pref.proot_startup_cmd.default
)

data class PrefGeneral(
    val resolution: String = Pref.general_resolution.default,
)

/** 顶部操作按钮类型 */
sealed interface SettingAction {
    data object RESET : SettingAction
    data object IMPORT : SettingAction
    data object EXPORT : SettingAction
}


class SettingViewModel : ViewModel() {
    // 一般设置

    var resolutionText by mutableStateOf("")
        private set

    val generalFLow = dataStore.data.map { pref ->
        val resolution = pref[general_resolution.key] ?: general_resolution.default
        PrefGeneral(
            resolution,
        )
    }
    val generalState = stateInSimple(PrefGeneral(), generalFLow)


    // proot设置
    val prootFlow = dataStore.data.map { pref ->
        PrefProot(
            pref[proot_bool_options.key] ?: proot_bool_options.default,
            pref[proot_startup_cmd.key] ?: proot_startup_cmd.default,
        )
    }
    val prootState = stateInSimple(PrefProot(), prootFlow)


    init {
        //resolutionText不随flow更改，初始化先读取一下
        viewModelScope.launch { resolutionText = generalFLow.first().resolution }
    }


    /** 点击重置按钮 */
    suspend fun resetSettings() = withContext(Dispatchers.IO) {
        dataStore.edit { pref -> Pref.allItems.forEach { item -> pref[item.key] = item.default } }
    }

    /** 点击导入按钮，从本地文件读取json转为用户偏好 */
    suspend fun importSettings(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        return@withContext kotlin.runCatching {
            val readResult = Utils.Files.readFromUri(ctx, uri)
            if (readResult.isFailure) throw readResult.exceptionOrNull()!!
            val map = Utils.Pref.deserializeFromJsonToMap(readResult.getOrNull()!!)
            dataStore.edit { preference ->
                for (entry in map) {
                    Pref.allItems.find { it.key.name == entry.key }?.let { item ->
                        preference[item.key] = entry.value
                    }
                }
            }
            return@runCatching
        }
    }

    /** 点击导出按钮. 将当前用户偏好转为json并写入本地文件 */
    suspend fun exportSettings(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        return@withContext kotlin.runCatching {
            val map = dataStore.data.map { preference ->
                val map = mutableMapOf<String, Any>()
                // 导出所有选项的值，如果没有修改过，就返回当前的默认值
                Pref.allItems.forEach { item -> map[item.key.name] = preference[item.key] ?: item.default }
                return@map map
            }.first()
            val json = Utils.Pref.serializeFromMapToJson(map)
            Utils.Files.writeToUri(ctx, uri, json).exceptionOrNull()?.let { throw it }
        }
    }


    fun onChangeProotBoolOptions(option: String, checked: Boolean) {
        val newValue = if (checked) prootState.value.boolOptions.plus(option)
        else prootState.value.boolOptions.minus(option)
        editDateStore(proot_bool_options.key, newValue)
    }

    fun onChangeProotStartupCmd(cmdRaw: String) {
        //换行 -> 空格， 去掉结尾 &, 去掉首尾空格
        editDateStore(proot_startup_cmd.key, cmdRaw.replace("\n", " ").trim().trimEnd('&').trim())
    }

    private val resolutionRegex = Regex("^(\\d+)(\\D+)(\\d+)$")
    private val resolutionRateLimiter  = RateLimiter()

    /** 格式化分辨率。如果格式不对返回null */
    fun formatResolution(text: String): String? = resolutionRegex.matchEntire(text.trim())?.let { matchResult ->
        val (_, w, _, h) = matchResult.groupValues
        if (w.isNotEmpty() && h.isNotEmpty()) "${w}x${h}"
        else null
    }

    /**
     * 分辨率TextField内容变更时的回调。
     * @param forceFormat 如果为 true, 则当传入text不符合格式规范时，将其改为一个符合规范的值并保存
     */
    fun onChangeResolutionText(text: String, forceFormat: Boolean) {
        Log.d(TAG, "onChangeResolutionText: 分辨率更改")
        resolutionText = text

        var formatted = formatResolution(text)
        if (forceFormat && formatted == null) {
            formatted = Pref.general_resolution.default
        }
        //如果符合格式，保存到本地
        if (formatted != null) {
            resolutionText = formatted //这个独立于flow之外所以要手动赋值
            Log.d(TAG, "onChangeResolutionText: 分辨率更改 - 格式正确，保存到本地")
            editDateStore(general_resolution.key, formatted)
            MainEmuActivity.instance.getPref().displayResolutionCustom.put(formatted)
        }
    }


}