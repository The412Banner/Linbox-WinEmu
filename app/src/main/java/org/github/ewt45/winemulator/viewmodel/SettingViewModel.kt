package org.github.ewt45.winemulator.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Utils.Ui.editDateStore
import org.github.ewt45.winemulator.Utils.Ui.stateInSimple
import org.github.ewt45.winemulator.dataStore
import org.github.ewt45.winemulator.Consts.Pref
import org.github.ewt45.winemulator.Consts.Pref.proot_bool_options
import org.github.ewt45.winemulator.Consts.Pref.proot_startup_cmd
import org.github.ewt45.winemulator.Utils
import kotlin.reflect.typeOf

private val TAG = "SettingViewModel"

data class PrefProot(
    /** 只会出现一次且没有附加参数的选项。有全名就尽量使用全名 */
    val proot_bool_options: Set<String> = Pref.proot_bool_options.default,
    val proot_startup_cmd: String = Pref.proot_startup_cmd.default
)

/** 顶部操作按钮类型 */
sealed interface SettingAction {
    data object RESET : SettingAction
    data object IMPORT : SettingAction
    data object EXPORT : SettingAction
}


class SettingViewModel : ViewModel() {
    val prootFlow = dataStore.data.map { pref ->
        PrefProot(
            pref[proot_bool_options.key] ?: proot_bool_options.default,
            pref[proot_startup_cmd.key] ?: proot_startup_cmd.default,
        )
    }
    val prootState = stateInSimple(PrefProot(), prootFlow)

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
        val newValue = if (checked) prootState.value.proot_bool_options.plus(option)
        else prootState.value.proot_bool_options.minus(option)
        editDateStore(proot_bool_options.key, newValue)
    }

    fun onChangeProotStartupCmd(cmdRaw: String) {
        //换行 -> 空格， 去掉结尾 &, 去掉首尾空格
        editDateStore(proot_startup_cmd.key, cmdRaw.replace("\n", " ").trim().trimEnd('&').trim())
    }


}