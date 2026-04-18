package org.github.ewt45.winemulator

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.system.Os
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlin.reflect.full.declaredMemberProperties

object Consts {
    private val TAG = "Consts"
    var isDebug = true
    lateinit var cacheDir: File

    /** Android path used as proot's /tmp bind target */
    lateinit var tmpDir: File

    /** Directory containing all rootfs installations — files/rootfs */
    lateinit var rootfsAllDir: File

    /** The currently active rootfs; should be a symlink to the actual rootfs — files/rootfs/current */
    lateinit var rootfsCurrDir: File

    /** XKB data directory required by termux-x11; can be obtained by installing libxkbcommon in the rootfs. Path: [rootfsCurrDir]/usr/share/X11/xkb */
    lateinit var rootfsCurrXkbDir: File

    /** /tmp inside the rootfs. Should be a symlink to [tmpDir] before starting proot. Path: [rootfsCurrDir]/tmp */
    lateinit var rootfsCurrTmpDir: File

    /** Directory where proot --link2symlink stores unique real files. Path: [rootfsCurrDir]/.l2s */
    lateinit var rootfsCurrL2sDir: File

    /** Initialization script executed when the container starts */
    lateinit var rootfsCurrStartSh: File

    /** A test alpine rootfs — files/rootfs/alpine-aarch64 */
    lateinit var alpineRootfsDir: File

    /** Name of the directory inside a rootfs that holds emulator configuration files. */
    val rootfsEmuConfDirName: String = "/.emuconf"

    /** proot binary — files/proot */
    lateinit var prootBin: File

    /** Directory that stores PulseAudio-related files */
    lateinit var pulseDir: File

    /** HOME directory set when running PulseAudio, used to locate ~/.config etc. */
    lateinit var pulseHomeDir: File
    lateinit var pulseBin: File

    /** Path to the installed APK file */
    lateinit var apkFilePath: String

    /** Default values defined in assets. These take precedence over in-code defaults. Key = DataStore key name, value = default value. */
    private lateinit var prefInAssets: Map<String, Any>

    object Ui {
        /** Width/height in dp when minimized */
        val minimizedIconSize = 48
    }

    /**
     * User preference definitions.
     * Defaults specified in assets take precedence over the in-code defaults here.
     */
    object Pref {
        data class Item<T>(val key: Preferences.Key<T>, val default: T, val flow: Flow<T>) {
            /** Returns the latest stored value, or [default] if nothing is stored. */
            suspend fun get(): T = flow.first()
        }

        /** All preference items — used for batch operations such as export, import, and reset. */
        val allItems by lazy { getAllPrefItems() }

        val general_resolution by item("general_resolution", "1280x720")
        val general_rootfs_lang by item("general_rootfs_lang", "en_US.utf8")
        val general_shared_ext_path by item("shared_ext_path", setOf("/storage/emulated/0/Download"))
        val proot_bool_options by item("proot_bool_options", setOf( "-L", "--link2symlink", "--sysvipc", "--kill-on-exit", /*"--root-id",*/))
        val proot_startup_cmd by item("proot_startup_cmd", "")

        // Input Controls Settings
        val inputcontrols_enabled by item("inputcontrols_enabled", false)
        val inputcontrols_profile_id by item("inputcontrols_profile_id", -1)
        val inputcontrols_opacity by item("inputcontrols_opacity", 0.4f)
        val inputcontrols_haptics by item("inputcontrols_haptics", true)
        
        // Theme Settings
        // Theme: 0 = follow system, 1 = dark, 2 = light
        val general_theme_mode by item("general_theme_mode", 1)

        // X11 Settings - touch mode: 0=virtual trackpad, 1=simulated touch, 2=touchscreen
        val x11_touch_mode by item("x11_touch_mode", 0)
        // X11 Settings - screen orientation: 10=follow system, 11=landscape, 12=portrait, 13=reverse landscape, 14=reverse portrait
        val x11_screen_orientation by item("x11_screen_orientation", 10)
        // X11 Settings - display scale: 30–300
        val x11_display_scale by item("x11_display_scale", 100)
        // X11 Settings - keep screen on
        val x11_keep_screen_on by item("x11_keep_screen_on", true)
        // X11 Settings - fullscreen mode
        val x11_fullscreen by item("x11_fullscreen", true)
        // X11 Settings - use display cutout area
        val x11_hide_cutout by item("x11_hide_cutout", true)
        // X11 Settings - picture-in-picture mode
        val x11_pip_mode by item("x11_pip_mode", false)
        // X11 Settings - resolution in "widthxheight" format, e.g. "1280x720"
        val x11_resolution by item("x11_resolution", "1280x720")

        /** Stored on this device only — excluded from export/import. */
        object Local {
            /** Name of the currently active rootfs (a folder inside [Consts.rootfsAllDir]). May be an empty string or point to a non-existent folder. */
            val curr_rootfs_name by item("local_curr_rootfs_name", "")
            /** JSON string mapping each rootfs name to the login user to use. Key = rootfs folder name, value = username. */
            val rootfs_login_user_json by item("local_rootfs_login_user", "{}")
            /** Skip the permission request flow */
            val skip_permissions by item("skip_permissions", false)
        }

        /**
         * Item initialization must happen after assets are loaded. With lazy, Consts should already be initialized the first time Pref is accessed.
         */
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> item(name: String, default: T): Lazy<Item<T>> = lazy {
            val key: Preferences.Key<T> = when (T::class) {
                Set::class -> stringSetPreferencesKey(name)
                String::class -> stringPreferencesKey(name)
                Boolean::class -> booleanPreferencesKey(name)
                Int::class -> intPreferencesKey(name)
                Float::class -> floatPreferencesKey(name)
                Long::class -> longPreferencesKey(name)
                Double::class -> doublePreferencesKey(name)
                else -> throw IllegalArgumentException("Unsupported type: ${T::class.simpleName}")
            } as Preferences.Key<T>
            val finalDefault = (prefInAssets[name].takeIf { it is T } ?: default) as T
            return@lazy Item(key, finalDefault, dataStore.data.map { it[key] ?: finalDefault })
        }

        /** Uses reflection to collect all preference items */
        @Suppress("UNCHECKED_CAST")
        private fun getAllPrefItems(): List<Item<Any>> {
            return Pref::class.declaredMemberProperties
                .filter { it.returnType.classifier == Pref.Item::class }
                .mapNotNull { property -> property.call(Pref) as? Pref.Item<Any> }
        }
    }

    //TODO Move expensive operations to async functions
    /**
     * Initializes Consts. Call once before first use.
     */
    fun init(ctx: Context) {
        isDebug = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        cacheDir = ctx.cacheDir
        cacheDir.mkdirs()

        tmpDir = File(cacheDir, "tmp")
        tmpDir.mkdirs()
        Os.chmod(tmpDir.absolutePath, "777".toInt(8))

        val fileDir = ctx.filesDir
        rootfsAllDir = File(fileDir, "rootfs")
        rootfsAllDir.mkdirs()

        rootfsCurrDir = File(rootfsAllDir, "current")
        alpineRootfsDir = File(rootfsAllDir, "alpine-aarch64") // created when extracting
        rootfsCurrXkbDir = File(rootfsCurrDir, "usr/share/X11/xkb")
        rootfsCurrTmpDir = File(rootfsCurrDir, "tmp")
        rootfsCurrL2sDir = File(rootfsCurrDir, "/.l2s")
        rootfsCurrStartSh = File(rootfsCurrDir, "/.emuconf/start.sh")

        // Extract proot from assets
        prootBin = File(fileDir, "proot")
        if (!prootBin.exists()) {
            Utils.streamCopy(ctx.assets.open("proot"), FileOutputStream(prootBin))
        }
        prootBin.setExecutable(true)

        pulseDir = File(ctx.filesDir, "pulseaudio")
        pulseHomeDir = File(pulseDir, "homedir")
        pulseBin = File(pulseDir, "pulseaudio")
        if (!pulseBin.exists()) {
            pulseDir.delete()
            Utils.Archive.decompressTarXz(ctx.assets.open("pulseaudio.tar.xz"), pulseDir)
            pulseHomeDir.mkdirs()
        }
        pulseBin.setExecutable(true)

        apkFilePath = ctx.applicationInfo.sourceDir

        // User preferences that take priority over in-code defaults
        val prefInAssetsJson = IOUtils.toString(ctx.assets.open("preferences.json"), StandardCharsets.UTF_8)
        prefInAssets = Utils.Pref.deserializeFromJsonToMap(prefInAssetsJson)
    }
}




