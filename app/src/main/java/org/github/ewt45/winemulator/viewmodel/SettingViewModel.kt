package org.github.ewt45.winemulator.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.system.Os
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.datastore.preferences.core.edit
import androidx.preference.PreferenceManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.Utils.Ui.editDateStore
import org.github.ewt45.winemulator.Utils.Ui.stateInSimple
import org.github.ewt45.winemulator.dataStore
import org.github.ewt45.winemulator.Consts.Pref
import org.github.ewt45.winemulator.Consts.Pref.Local.rootfs_login_user_json
import org.github.ewt45.winemulator.Consts.Pref.general_resolution
import org.github.ewt45.winemulator.Consts.Pref.general_rootfs_lang
import org.github.ewt45.winemulator.Consts.Pref.general_shared_ext_path
import org.github.ewt45.winemulator.Consts.Pref.inputcontrols_enabled
import org.github.ewt45.winemulator.Consts.Pref.inputcontrols_haptics
import org.github.ewt45.winemulator.Consts.Pref.inputcontrols_opacity
import org.github.ewt45.winemulator.Consts.Pref.inputcontrols_profile_id
import org.github.ewt45.winemulator.Consts.Pref.proot_bool_options
import org.github.ewt45.winemulator.Consts.Pref.proot_startup_cmd
import org.github.ewt45.winemulator.Consts.Pref.general_theme_mode
import org.github.ewt45.winemulator.Consts.Pref.x11_touch_mode
import org.github.ewt45.winemulator.Consts.Pref.x11_screen_orientation
import org.github.ewt45.winemulator.Consts.Pref.x11_display_scale
import org.github.ewt45.winemulator.Consts.Pref.x11_keep_screen_on
import org.github.ewt45.winemulator.Consts.Pref.x11_fullscreen
import org.github.ewt45.winemulator.Consts.Pref.x11_hide_cutout
import org.github.ewt45.winemulator.Consts.Pref.x11_pip_mode
import org.github.ewt45.winemulator.Consts.Pref.x11_resolution
import org.github.ewt45.winemulator.Consts.rootfsAllDir
import org.github.ewt45.winemulator.Consts.rootfsCurrDir
import org.github.ewt45.winemulator.FuncOnChangeAction
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.RateLimiter
import org.github.ewt45.winemulator.Utils
import org.github.ewt45.winemulator.Utils.Ui.editDateStoreAsync
import org.github.ewt45.winemulator.emu.ProotRootfs
import java.io.File

private val TAG = "SettingViewModel"


/**
 * Data modification flow: user edits -> calls ViewModel function -> modifies DataStore (editDateStore) -> triggers flow emit -> propagates to state -> triggers Compose recomposition
 *
 * When adding a new property, remember to update the corresponding flow's map to pass it in the new instance constructor
 */
class SettingViewModel : ViewModel() {

    // SharedPreferences used to sync X11 settings into a format readable by termux-x11
    private var sharedPrefs: SharedPreferences? = null

    /** Initialize SharedPreferences — must be called from the Activity */
    fun initSharedPreferences(context: Context) {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    /** Sync current X11 settings to SharedPreferences — reads from DataStore directly to avoid crashes */
    fun syncX11SettingsToSharedPrefs() {
        val prefs = sharedPrefs ?: return
        viewModelScope.launch {
            try {
                val data = dataStore.data.first()
                prefs.edit().apply {
                    // Touch mode: 0=virtual touchpad(1), 1=simulated touch(2), 2=touchscreen(3)
                    val touchMode = data[x11_touch_mode.key] ?: x11_touch_mode.default
                    putString("touchMode", (touchMode + 1).toString())
                    // Screen orientation: 10=auto, 11=landscape, 12=portrait, 13=reverseLandscape, 14=reversePortrait
                    val orientationMap = mapOf(10 to "auto", 11 to "landscape", 12 to "portrait", 13 to "reverseLandscape", 14 to "reversePortrait")
                    val orientation = data[x11_screen_orientation.key] ?: x11_screen_orientation.default
                    putString("forceOrientation", orientationMap[orientation] ?: "auto")
                    // Display scale
                    val scale = data[x11_display_scale.key] ?: x11_display_scale.default
                    putInt("displayScale", scale)
                    // Keep screen on
                    val keepScreenOn = data[x11_keep_screen_on.key] ?: x11_keep_screen_on.default
                    putBoolean("keepScreenOn", keepScreenOn)
                    // Resolution: use general_resolution to stay consistent with existing logic
                    val resolution = data[general_resolution.key] ?: general_resolution.default
                    if (resolution.contains("x")) {
                        putString("displayResolutionMode", "custom")
                        putString("displayResolutionCustom", resolution)
                    }
                    apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "syncX11SettingsToSharedPrefs failed", e)
            }
        }
    }

    // General settings
    var resolutionText by mutableStateOf("")
        private set

    /** Keys are the rootfs list under [Consts.rootfsAllDir], values are all available users for each rootfs.
     * Does not include [Consts.rootfsCurrDir]. Currently used as a substitute for rootfsList. Could later be changed to a list of rootfs info objects (though non-strings can't be stored in DataStore). */
    val rootfsUsersOptions = mutableStateOf(mapOf<String, List<ProotRootfs.UserInfo>>())

    /** Rootfs alias map — key is folder name, value is alias */
    val rootfsAliasMap = mutableStateOf(mapOf<String, String>())

    val generalFLow = dataStore.data.map { pref ->
        PrefGeneral(
            general_resolution.run { pref[key] ?: default },
            general_shared_ext_path.run { pref[key] ?: default },
            general_rootfs_lang.run { pref[key] ?: default },
            Json.decodeFromString(rootfs_login_user_json.run { pref[key] ?: default }),
        )
    }
    val generalState = stateInSimple(PrefGeneral_DEFAULT, generalFLow)

    // PRoot settings
    val prootFlow = dataStore.data.map { pref ->
        PrefProot(
            proot_bool_options.run { pref[key] ?: default },
            proot_startup_cmd.run { pref[key] ?: default },
        )
    }
    val prootState = stateInSimple(PrefProot_DEFAULT, prootFlow)

    // InputControls settings
    val inputControlsFlow = dataStore.data.map { pref ->
        PrefInputControls(
            inputcontrols_enabled.run { pref[key] ?: default },
            inputcontrols_profile_id.run { pref[key] ?: default },
            inputcontrols_opacity.run { pref[key] ?: default },
            inputcontrols_haptics.run { pref[key] ?: default },
        )
    }
    val inputControlsState = stateInSimple(PrefInputControls_DEFAULT, inputControlsFlow)
    
    // Theme settings
    val themeFlow = dataStore.data.map { pref ->
        general_theme_mode.run { pref[key] ?: default }
    }
    val themeState = stateInSimple(1, themeFlow)

    // X11 settings
    val x11Flow = dataStore.data.map { pref ->
        PrefX11(
            x11_touch_mode.run { pref[key] ?: default },
            x11_screen_orientation.run { pref[key] ?: default },
            x11_display_scale.run { pref[key] ?: default },
            x11_keep_screen_on.run { pref[key] ?: default },
            x11_fullscreen.run { pref[key] ?: default },
            x11_hide_cutout.run { pref[key] ?: default },
            x11_pip_mode.run { pref[key] ?: default },
            x11_resolution.run { pref[key] ?: default },
        )
    }
    val x11State = stateInSimple(PrefX11_DEFAULT, x11Flow)

    init {
        // Some data won't auto-update — refresh it in [updateValuesWhenEnterSettings] to ensure the latest values are fetched when entering the settings screen
    }

    /**
     * When entering the settings screen, some data (e.g. local file lists) may have changed but the in-memory data won't reflect that. Refresh it here.
     */
    fun updateValuesWhenEnterSettings() {
        Log.e(TAG, "updateValuesWhenEnterSettings: testing whether skipping this function causes stale data")
        viewModelScope.launch(IO) {
            resolutionText = general_resolution.get() // resolutionText doesn't update via flow, read it once on init
            rootfsUsersOptions.value = getRootfsUsersOptions()
            // Update alias map
            rootfsAliasMap.value = getRootfsList().associateWith { Utils.Rootfs.getAlias(File(rootfsAllDir, it)) }
            // localRootfsLoginUsersMap currently depends on rootfsUsersOptions to work correctly, so also refresh manually
            onChangeRootfsLoginUser("", "")
        }
    }

    /** Reset button tapped */
    suspend fun resetSettings() = withContext(IO) {
        dataStore.edit { pref -> Pref.allItems.forEach { item -> pref[item.key] = item.default } }
    }

    /** Import button tapped — reads JSON from a local file and applies it as user preferences */
    suspend fun importSettings(ctx: Context, uri: Uri) = withContext(IO) {
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

    /** Export button tapped — serializes current user preferences to JSON and writes it to a local file */
    suspend fun exportSettings(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        return@withContext kotlin.runCatching {
            val map = dataStore.data.map { preference ->
                val map = mutableMapOf<String, Any>()
                // Export all option values; if a value was never changed, return its current default
                Pref.allItems.forEach { item -> map[item.key.name] = preference[item.key] ?: item.default }
                return@map map
            }.first()
            val json = Utils.Pref.serializeFromMapToJson(map)
            Utils.Files.writeToUri(ctx, uri, json).exceptionOrNull()?.let { throw it }
        }
    }

    suspend fun onChangeProotBoolOptions(option: String, checked: Boolean) = withContext(IO) {
        val newValue = if (checked) prootState.value.boolOptions + option
        else prootState.value.boolOptions - option
        editDateStore(proot_bool_options.key, newValue)
    }

    fun onChangeProotStartupCmd(cmdRaw: String) {
        // newline -> space, strip trailing &, trim whitespace
        editDateStoreAsync(proot_startup_cmd.key, cmdRaw.replace("\n", " ").trim().trimEnd('&').trim())
    }

    private val resolutionRegex = Regex("^(\\d+)(\\D+)(\\d+)$")
    private val resolutionRateLimiter = RateLimiter()

    /** Format resolution string. Returns null if the format is invalid. */
    fun formatResolution(text: String): String? = resolutionRegex.matchEntire(text.trim())?.let { matchResult ->
        val (_, w, _, h) = matchResult.groupValues
        if (w.isNotEmpty() && h.isNotEmpty()) "${w}x${h}"
        else null
    }

    /**
     * Callback when the resolution TextField content changes.
     * @param forceFormat If true, when the text doesn't match the expected format it will be replaced with a valid value and saved
     */
    fun onChangeResolutionText(text: String, forceFormat: Boolean) {
        Log.d(TAG, "onChangeResolutionText: resolution changed")
        resolutionText = text

        var formatted = formatResolution(text)
        if (forceFormat && formatted == null) {
            formatted = Pref.general_resolution.default
        }
        // If format is valid, save to local storage
        if (formatted != null) {
            resolutionText = formatted // This lives outside the flow, so assign manually
            Log.d(TAG, "onChangeResolutionText: resolution changed - format valid, saving to storage")
            editDateStoreAsync(general_resolution.key, formatted)
            MainEmuActivity.instance.getPref().displayResolutionCustom.put(formatted)
        }
    }

    /**
     * Add or remove an external shared directory
     */
    suspend fun onChangeShareExtPath(oldPath: String, newPath: String, action: FuncOnChangeAction) = withContext(Dispatchers.IO) {
        val newList = general_shared_ext_path.get().run {
            when (action) {
                FuncOnChangeAction.EDIT -> minus(oldPath).plus(newPath)
                FuncOnChangeAction.ADD -> plus(newPath)
                FuncOnChangeAction.DEL -> minus(newPath)
            }
        }
        editDateStore(general_shared_ext_path.key, newList)
    }

    /** Returns the current rootfs list, excluding [rootfsCurrDir] */
    fun getRootfsList(): List<String> = rootfsAllDir.list()?.toMutableList()?.minus(rootfsCurrDir.name) ?: listOf()

    /**
     * Rename or delete a rootfs directory.
     * On rename: the new name must not equal [Consts.rootfsCurrDir] or any existing name.
     * On delete: [rootfsCurrDir] must not point to the rootfs being deleted.
     * @throws Exception on failure
     */
    @Throws(Exception::class)
    suspend fun onChangeRootfsName(oldName: String, newName: String, action: FuncOnChangeAction) = withContext(IO) {
        when (action) {
            FuncOnChangeAction.EDIT -> {
                if (newName == rootfsCurrDir.name || File(rootfsAllDir, newName).exists())
                    throw RuntimeException("A file with that name already exists, cannot rename")
                File(rootfsAllDir, oldName).renameTo(File(rootfsAllDir, newName))
                // After renaming the rootfs, also rename its key in the login user map
                onChangeRootfsLoginUser(newName, ProotRootfs.getPreferredUser(newName).name)
            }

            FuncOnChangeAction.ADD -> Unit
            FuncOnChangeAction.DEL -> {
                // Check if this is the currently running rootfs (the one the 'current' symlink points to)
                if (rootfsCurrDir.exists() && newName == rootfsCurrDir.canonicalFile.name)
                    throw RuntimeException("This rootfs is currently running and cannot be deleted")
                // Bound directories (dev, proc) cannot be deleted with Java APIs; rm -r works fine
                val output = Utils.readLinesProcessOutput(ProcessBuilder(listOf("sh","-c", "rm -r ${File(rootfsAllDir, oldName).absolutePath}"))
                    .redirectErrorStream(true).start())
                if (output.isNotBlank()) throw RuntimeException(output)
                // After deleting the rootfs, also remove its key from the login user map (a non-existent userName gets pruned)
                onChangeRootfsLoginUser(newName, "stub")
            }
        }
    }

    /** Reads the full list of available users for each rootfs using [ProotRootfs.getUserInfos]. Excludes [rootfsCurrDir]. */
    private fun getRootfsUsersOptions(): Map<String, List<ProotRootfs.UserInfo>> =
        getRootfsList().associateWith { rootfs -> ProotRootfs.getUserInfos(File(rootfsAllDir, rootfs)) }

    suspend fun onChangeRootfsSelect(rootfsName: String) {
        MainEmuActivity.instance.terminalViewModel.stopTerminal()
        Utils.Rootfs.makeCurrent(File(rootfsAllDir, rootfsName))
        MainEmuActivity.instance.finish()
    }

    /** Change the default login user for a rootfs. Internally calls [getRootfsUsersOptions] to refresh [rootfsUsersOptions]. */
    suspend fun onChangeRootfsLoginUser(rootfsName: String, userName: String) {
        rootfsUsersOptions.value = getRootfsUsersOptions()
        val unfilteredMap = generalState.value.localRootfsLoginUsersMap.plus(rootfsName to userName)
        val newMap = mutableMapOf<String, String>()
        // Ensure newMap contains all rootfs keys, each mapped to a valid value
        rootfsUsersOptions.value.forEach { (k, v) ->
            newMap[k] = ProotRootfs.getPreferredUser(unfilteredMap[k], v).name
        }
        editDateStore(rootfs_login_user_json.key, Json.encodeToString(newMap))
    }

    fun onChangeRootfsLang(lang: String) = editDateStoreAsync(general_rootfs_lang.key, lang)

    /** Update the display alias for a rootfs */
    fun onChangeRootfsAlias(rootfsName: String, newAlias: String) {
        Utils.Rootfs.setAlias(File(rootfsAllDir, rootfsName), newAlias)
        // Update the in-memory alias map
        rootfsAliasMap.value = rootfsAliasMap.value.toMutableMap().apply { this[rootfsName] = newAlias }
    }

    // InputControls settings
    fun onChangeInputControlsEnabled(enabled: Boolean) = editDateStoreAsync(inputcontrols_enabled.key, enabled)
    fun onChangeInputControlsProfileId(profileId: Int) = editDateStoreAsync(inputcontrols_profile_id.key, profileId)
    fun onChangeInputControlsOpacity(opacity: Float) = editDateStoreAsync(inputcontrols_opacity.key, opacity)
    fun onChangeInputControlsHaptics(haptics: Boolean) = editDateStoreAsync(inputcontrols_haptics.key, haptics)
    
    // Theme settings
    fun onChangeThemeMode(mode: Int) = editDateStoreAsync(general_theme_mode.key, mode)

    // X11 settings — write directly to SharedPreferences for immediate effect
    fun onChangeX11TouchMode(mode: Int) {
        // Write directly to SharedPreferences so the change takes effect immediately
        sharedPrefs?.edit()?.putString("touchMode", (mode + 1).toString())?.apply()
        // Also persist to DataStore
        editDateStoreAsync(x11_touch_mode.key, mode)
    }
    fun onChangeX11ScreenOrientation(orientation: Int) {
        // Write directly to SharedPreferences so the change takes effect immediately
        val orientationMap = mapOf(10 to "auto", 11 to "landscape", 12 to "portrait", 13 to "reverseLandscape", 14 to "reversePortrait")
        sharedPrefs?.edit()?.putString("forceOrientation", orientationMap[orientation] ?: "auto")?.apply()
        // Also persist to DataStore
        editDateStoreAsync(x11_screen_orientation.key, orientation)
    }
    fun onChangeX11DisplayScale(scale: Int) {
        // Write directly to SharedPreferences so the change takes effect immediately
        sharedPrefs?.edit()?.putInt("displayScale", scale)?.apply()
        // Also persist to DataStore
        editDateStoreAsync(x11_display_scale.key, scale)
    }
    fun onChangeX11KeepScreenOn(enabled: Boolean) {
        // Write directly to SharedPreferences so the change takes effect immediately
        sharedPrefs?.edit()?.putBoolean("keepScreenOn", enabled)?.apply()
        // Also persist to DataStore
        editDateStoreAsync(x11_keep_screen_on.key, enabled)
    }

    /**
     * Returns the login username for the currently selected rootfs.
     * Reads from rootfs_login_user_json for the current rootfs entry.
     */
    suspend fun getCurrentLoginUser(): String {
        try {
            val currentRootfsName = Consts.rootfsCurrDir.canonicalFile.name
            // Read fresh data directly from DataStore rather than potentially stale cached state
            val jsonString = dataStore.data.first()[rootfs_login_user_json.key] ?: "{}"
            val loginUsersMap: Map<String, String> = Json.decodeFromString(jsonString)
            return loginUsersMap[currentRootfsName] ?: "root"
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLoginUser failed: ${e.message}")
            return "root"
        }
    }

}

data class PrefProot(
    /** Flags that appear at most once with no additional arguments. Use the long form whenever available. */
    val boolOptions: Set<String>,
    val startupCmd: String,
)

private val PrefProot_DEFAULT = PrefProot(
    proot_bool_options.default,
    proot_startup_cmd.default,
)

data class PrefGeneral(
    val resolution: String,
    val sharedExtPath: Set<String>,
    val rootfsLang: String,
    /** Login user for every rootfs. Key = rootfs name, value = username. */
    val localRootfsLoginUsersMap: Map<String, String>,
)

private val PrefGeneral_DEFAULT = PrefGeneral(
    general_resolution.default,
    general_shared_ext_path.default,
    general_rootfs_lang.default,
    mapOf(),
)

data class PrefInputControls(
    val enabled: Boolean,
    val profileId: Int,
    val opacity: Float,
    val haptics: Boolean,
)

private val PrefInputControls_DEFAULT = PrefInputControls(
    inputcontrols_enabled.default,
    inputcontrols_profile_id.default,
    inputcontrols_opacity.default,
    inputcontrols_haptics.default,
)

data class PrefX11(
    val touchMode: Int,
    val screenOrientation: Int,
    val displayScale: Int,
    val keepScreenOn: Boolean,
    val fullscreen: Boolean,
    val hideCutout: Boolean,
    val pipMode: Boolean,
    val resolution: String,
)

private val PrefX11_DEFAULT = PrefX11(
    x11_touch_mode.default,
    x11_screen_orientation.default,
    x11_display_scale.default,
    x11_keep_screen_on.default,
    x11_fullscreen.default,
    x11_hide_cutout.default,
    x11_pip_mode.default,
    x11_resolution.default,
)

/** Top action button types */
sealed interface SettingAction {
    data object RESET : SettingAction
    data object IMPORT : SettingAction
    data object EXPORT : SettingAction
}