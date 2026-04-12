package org.github.ewt45.winemulator.emu

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.Consts.tmpDir
import org.github.ewt45.winemulator.Consts.rootfsCurrL2sDir
import org.github.ewt45.winemulator.Utils.chmod
import java.io.File

class Proot {
    private val TAG = "Proot"

    suspend fun attach(): ProcessBuilder = withContext(Dispatchers.IO) {
        val rootfs = Consts.rootfsCurrDir
        val prootBin = Consts.prootBin
        prootBin.setExecutable(true)
        
        val lang = Consts.Pref.general_rootfs_lang.get()
        
        // 确保 link2symlink 目录存在
        rootfsCurrL2sDir.mkdirs()
        chmod(rootfsCurrL2sDir, "755")
        
        ProotHelper.setup_fake_data()
        
        val userInfo = ProotRootfs.getPreferredUser(rootfs.canonicalFile.name)
        Log.d(TAG, "启动 Proot，目标用户: ${userInfo.name} (UID: ${userInfo.uid})")

        // 1. 核心参数
        val prootArgs = mutableListOf(
            prootBin.absolutePath,
            "-0",                     // Root 映射，解决 I have no name! 问题
            "--link2symlink",         // 解决 Android f2fs 上的文件属性问题
            "--sysvipc",              // 支持进程间通信
            "--kill-on-exit",         // 主进程退出时清理子进程
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/storage",
            "-b", "/system",
            "-b", "${tmpDir.absolutePath}:/tmp",           // 临时目录
            "-b", "${rootfs.absolutePath}/tmp:/dev/shm",   // 共享内存
            "-b", "/proc/self/fd:/dev/fd",                 // 文件描述符
            "-b", "/dev/urandom:/dev/random",              // 随机数
            "-w", userInfo.home
        )

        // SELinux 伪装 - 某些程序检测 SELinux 会失败
        val selinuxEmpty = File(rootfs, "sys/.empty")
        if (selinuxEmpty.exists() || runCatching { selinuxEmpty.parentFile?.mkdirs(); selinuxEmpty.createNewFile() }.getOrDefault(false)) {
            prootArgs.add("-b")
            prootArgs.add("${selinuxEmpty.absolutePath}:/sys/fs/selinux")
        }

        // 动态绑定用户配置的外部存储路径
        val sharedPaths = Consts.Pref.general_shared_ext_path.get()
        sharedPaths.forEach { path ->
            if (File(path).exists()) {
                prootArgs.add("-b")
                prootArgs.add(path)
            }
        }

        // 如果不是登录 root，则使用 change-id
        if (userInfo.uid != 0L) {
            prootArgs.add("--change-id=${userInfo.uid}")
        }

        // 2. 构建环境变量 - 先读取 /etc/environment
        val loginEnvs = mutableMapOf<String, String>()
        readEtcEnvironment(rootfs, loginEnvs)
        
        // 覆盖/添加必要的环境变量
        loginEnvs["TERM"] = "xterm-256color"
        loginEnvs["HOME"] = userInfo.home
        loginEnvs["USER"] = userInfo.name
        loginEnvs["LOGNAME"] = userInfo.name
        loginEnvs["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        loginEnvs["SHELL"] = userInfo.shell
        loginEnvs["LANG"] = lang
        loginEnvs["TMPDIR"] = "/tmp"
        loginEnvs["DISPLAY"] = ":13"                              // X11 显示
        loginEnvs["PULSE_SERVER"] = "tcp:127.0.0.1:4713"          // 音频
        loginEnvs["PROOT_NO_SECCOMP"] = "1"                       // Android 12+ 兼容

        // 3. 组装最终命令
        val finalCommand = mutableListOf<String>().apply {
            addAll(prootArgs)
            add("/usr/bin/env")
            add("-i") // 彻底清除宿主环境变量污染
            loginEnvs.forEach { (k, v) -> add("$k=$v") }
            add(userInfo.shell)
            add("-l") // 登录模式，加载 /etc/profile 等
        }

        lastTimeCmd = finalCommand.joinToString(" ")
        Log.d(TAG, "attach: 最终命令=$lastTimeCmd")

        return@withContext ProcessBuilder(finalCommand)
            .directory(rootfs)
            .also {
                it.environment().clear()
                it.environment()["PROOT_TMP_DIR"] = tmpDir.absolutePath
                it.environment()["PROOT_NO_SECCOMP"] = "1"
                it.environment()["LD_PRELOAD"] = ""  // 防止库冲突
            }
            .redirectErrorStream(true)
    }

    /**
     * 读取 /etc/environment 下的环境变量并添加到 envMap
     */
    private fun readEtcEnvironment(rootfs: File, envMap: MutableMap<String, String>) {
        try {
            for (line in File(rootfs, "/etc/environment").readLines()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith('#') && trimmed.contains('=')) {
                    val (key, value) = trimmed.split('=', limit = 2)
                    envMap[key.trim()] = value.trim('"')
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 /etc/environment 失败: ${e.message}")
        }
    }

    companion object {
        var lastTimeCmd = ""
    }
}