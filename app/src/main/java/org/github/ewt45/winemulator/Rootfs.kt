package org.github.ewt45.winemulator

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Consts.rootfsDir
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.URL

object Rootfs {
    /**
     * 检查rootfs是否存在。若不存在，则下载并解压
     */
    suspend fun ensure(a: Activity):Unit = withContext(Dispatchers.IO) {
        //判断条件：rootfs文件夹存在且内容不为空
        if (rootfsDir.exists() && rootfsDir.isDirectory
            && rootfsDir.list()?.isEmpty() == false) {
            return@withContext
        }

        val rootfsFile = File(Consts.cacheDir, "ubuntu-rootfs.tar.gz")
        if (rootfsFile.exists()) {
            if (Utils.calculateSha256(rootfsFile).lowercase() == "91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa")
                return@withContext
            else
                rootfsFile.delete()
        }

        //下载
        val url = URL("https://github.com/termux/proot-distro/releases/download/v4.18.0/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz")
        url.openStream().use { input ->
            FileOutputStream(rootfsFile).use { output ->
                input.copyTo(output)
            }
        }

        //解压
        val process = ProcessBuilder("tar", "-xJf", rootfsFile.absolutePath, "-C", rootfsDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        var output:String
        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
            output = lines.joinToString(separator = "\n")
        }
        process.waitFor()
        Log.d("Rootfs", "ensure: 执行tar解压完成。输出：\n$output")

    }


    fun runProotCommand(context: Context, rootfs: File, command: String): String {
        val prootPath = File(context.filesDir, "proot").absolutePath
        val output = StringBuilder()

        val process = ProcessBuilder(
            prootPath,
            "-R", rootfs.absolutePath,
            "/bin/sh", "-c", command
        )
            .directory(context.filesDir)
            .redirectErrorStream(true)
            .start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        reader.useLines { lines ->
            output.appendLine(lines.joinToString("\n"))
        }

        process.waitFor()
        return output.toString()
    }



}