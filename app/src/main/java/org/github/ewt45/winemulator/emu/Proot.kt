package org.github.ewt45.winemulator.emu

import android.util.Log
import androidx.compose.ui.text.toLowerCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.apache.commons.io.FileUtils
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.Consts.Pref.general_shared_ext_path
import org.github.ewt45.winemulator.Consts.Pref.proot_bool_options
import org.github.ewt45.winemulator.Consts.rootfsCurrL2sDir
import org.github.ewt45.winemulator.Utils.chmod
import org.github.ewt45.winemulator.emu.ProotHelper.DEFAULT_FAKE_KERNEL_VERSION
import java.io.File
import java.nio.charset.StandardCharsets

/** Linux terminal connector. Sends commands and reads output. */
class Proot {
    private val TAG = "Proot"

    companion object {
        /** Full command from the last proot invocation — for display only; may not be directly re-executable. */
        var lastTimeCmd = ""
    }

    suspend fun attach(): ProcessBuilder = withContext(Dispatchers.IO) {
        val rootfs = Consts.rootfsCurrDir
        val tmpdir = Consts.tmpDir
        val lang = Consts.Pref.general_rootfs_lang.get()

        // TODO: clear tmp before each run. But not after tx11 starts — the container would lose its connection
//        tmpdir.deleteRecursively()
//        tmpdir.mkdirs()
//        chmod(tmpdir, "1777")

        rootfsCurrL2sDir.mkdirs()
        chmod(rootfsCurrL2sDir, "755")
//        ProotHelper.createStartSh()
        ProotHelper.setup_fake_data()
        editEtcLocaleGen(rootfs, lang)

        // proot arguments largely follow the Proot-Distro approach

        // Login with the preferred user (non-root preferred). uid/gid/home/shell are read from /etc/passwd.
        val userInfo = ProotRootfs.getPreferredUser(rootfs.canonicalFile.name)

        val prootCmd = mutableListOf(
            Consts.prootBin.absolutePath,
//            "--root-id", // login as root
            *proot_bool_options.get().toTypedArray(), // general proot flags; user-configurable. defaults: "-L","--link2symlink","--kill-on-exit","--sysvipc"
            "--kernel-release=$DEFAULT_FAKE_KERNEL_VERSION",
            "--rootfs=${rootfs.absolutePath}",
            "--change-id=${userInfo.uid}:${userInfo.gid}",
            "--cwd=${userInfo.home}",
            "--bind=${tmpdir.absolutePath}:/tmp",
            "--bind=${rootfs.absolutePath}/tmp:/dev/shm", // reuse tmp as /dev/shm — potential conflict?
            "--bind=/sys",
            "--bind=/proc/self/fd:/dev/fd",
            "--bind=/proc",
            "--bind=/dev/urandom:/dev/random",
            "--bind=/dev",
//            "--bind=/storage/emulated/0/Download",
        )

        // proot-distro binds these unconditionally, but in practice they already exist
        File("/dev/stderr").takeIf { !it.exists() }?.let {
            prootCmd.add("--bind=/proc/self/fd/2:/dev/stderr")
        }
        File("/dev/stdout").takeIf { !it.exists() }?.let {
            prootCmd.add("--bind=/proc/self/fd/1:/dev/stdout")
        }
        File("/dev/stdin").takeIf { !it.exists() }?.let {
            prootCmd.add("--bind=/proc/self/fd/0:/dev/stdin")
        }

        ProotHelper.setup_fake_data()
        prootCmd.add("--bind=${rootfs.absolutePath}/sys/.empty:/sys/fs/selinux") // pretend SELinux is absent
        prootCmd.addAll(
            mapOf(
                "/proc/.loadavg" to "/proc/loadavg",
                "/proc/.stat" to "/proc/stat",
                "/proc/.uptime" to "/proc/uptime",
                "/proc/.version" to "/proc/version",
                "/proc/.vmstat" to "/proc/vmstat",
                "/proc/.sysctl_entry_cap_last_cap" to "/proc/sys/kernel/cap_last_cap",
                "/proc/.sysctl_inotify_max_user_watches" to "/proc/sys/fs/inotify/max_user_watches",
            ).mapNotNull { bindIfNotReadable(rootfs, it.key, it.value) })



        prootCmd.addAll(general_shared_ext_path.get().map { bindPath ->
            File(rootfs, bindPath).runCatching { takeIf { FileUtils.isSymlink(it) }?.delete() }
            "--bind=$bindPath"
        })


        val loginEnvs = EnvMap()
        // Read /etc/environment first; any later override uses override=true. Reading it after setting other vars could cause values to be prepended rather than replaced.
        readEtcEnvironment(rootfs, loginEnvs)
//        loginEnvs.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/games:/usr/games") // should already be set in /etc/environment
//        loginEnvs.put("LD_LIBRARY_PATH","/usr/lib/aarch64-linux-gnu:/usr/lib/arm-linux-gnueabihf")
//        loginEnvs.put("LC_ALL", "en_US.UTF-8", true) // LC_ALL for debug only — overrides LANG and all LC_* vars
        loginEnvs.put("LANG", lang, true) // overrides locale vars not set via LC_*
        loginEnvs.put("HOME", userInfo.home, true)
        loginEnvs.put("USER", userInfo.name, true)
        loginEnvs.put("TMPDIR", "/tmp", true)
        loginEnvs.put("DISPLAY", ":13", true)
        loginEnvs.put("PULSE_SERVER", "tcp:127.0.0.1:4713", true)
        // After installing something (mesa-dri-gallium? mesa-gles?), xfce4 stopped launching without this env var (no output, no exit)
//            "LIBGL_ALWAYS_SOFTWARE=1",

        prootCmd.addAll(
            listOf(
                "/usr/bin/env",
                "-i",
                *loginEnvs.toArray(),
                userInfo.shell, "-l", // -l: interactive (login) shell; -c: run a command and exit
            )
        )


        val prootCmdProotPart = prootCmd.toMutableList()
        prootCmd.clear()
        // sh -c expects a single string argument — do not split further
        prootCmd.addAll(listOf("sh", "-c", prootCmdProotPart.joinToString(" "))) //umask 0022 ; // TODO: skip umask for now?
        lastTimeCmd = "sh -c \\\n" + prootCmdProotPart.joinToString(" \\\n")
        Log.d(TAG, "attach: final prootcmd=$lastTimeCmd")

        val processBuilder = ProcessBuilder(prootCmd)
            .directory(rootfs)
            .also {
                it.environment()["PROOT_TMP_DIR"] = Consts.tmpDir.absolutePath
                it.environment()["LD_PRELOAD"] = ""
                // FIXME: setting PROOT_L2S_DIR breaks locale-gen. Removed temporarily; investigate and re-enable.
//                it.environment()["PROOT_L2S_DIR"] = rootfsCurrL2sDir.absolutePath // link2symlink related
            }
            .redirectErrorStream(true)

        return@withContext processBuilder
    }

    /** Reads environment variables from /etc/environment and adds them to [envMap]. */
    private fun readEtcEnvironment(rootfs: File, envMap: EnvMap) {
        try {
            for (l in File(rootfs, "/etc/environment").readLines()) {
                val line = l.trim()
                line.takeIf { !line.startsWith('#') && line.contains('=') }?.let {
                    val split = line.split("=", limit = 2)
                    envMap.put(split[0], split[1].trim('\"'))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Edits /etc/locale.gen to enable the target locale; locale-gen runs afterwards to generate the locale files. */
    private fun editEtcLocaleGen(rootfs: File, targetLocale: String) {
        try {
            val file = File(rootfs, "/etc/locale.gen").takeIf { it.exists() } ?: return
            val regexCharNum = "[^a-zA-Z0-9]".toRegex()
            val lines = FileUtils.readLines(file, StandardCharsets.UTF_8).map {
                // Find the target locale line and strip the leading comment if present
                val uncommentLine = it.trimStart('#').trim()
                val locale = uncommentLine.split(' ').takeIf { parts -> parts.size == 2 }?.get(0) ?: return@map it
                val comp1 = locale.replace(regexCharNum, "").lowercase()
                val comp2 = targetLocale.replace(regexCharNum, "").lowercase()
                return@map if (comp1 == comp2) uncommentLine else it
            }
            FileUtils.writeLines(file, lines)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Returns null if the file exists and is readable; otherwise returns this file. */
    private fun File.takeIfCantRead(): File? {
        return try {
            takeUnless { it.exists() && it.canRead() }
        } catch (e: Exception) {
            this
        }
    }


    /**
     * Binds File(rootfs, [bindFrom]) to [bindTo] if [bindTo] is not readable.
     * @param bindTo absolute Android path; used as the proot bind target when unreadable
     * @param bindFrom path of a file inside the rootfs (relative to rootfs root)
     * @return a --bind string, or null if binding was not needed
     */
    private fun bindIfNotReadable(rootfs: File, bindFrom: String, bindTo: String): String? {
        return File(bindTo).takeIfCantRead()?.let { "--bind=${File(rootfs, bindFrom).absolutePath}:$bindTo" }
    }


}

class EnvMap {
    val map = mutableMapOf<String, String>()

    /**
     * Adds or updates an environment variable. Prepends [v] before the existing value unless [override] is true, in which case the existing value is replaced.
     */
    fun put(k: String, v: String, override: Boolean = false) {
        val k1 = k.trim()
        val v1 = v.trim()
        if (k1.contains("=")) Log.w("TAG", "key must not contain '=': key=$k1  value=$v1")
        val oldV = map[k1]
        map[k1] = if (oldV != null && !override) "$v1:$oldV" else v1
    }

    fun get(k: String): String = map.getOrDefault(k, "")

    /** Returns all environment variables as an array of "k=v" strings. */
    fun toArray(): Array<String> = map.toList().map { "${it.first}=${it.second}" }.toTypedArray()
}