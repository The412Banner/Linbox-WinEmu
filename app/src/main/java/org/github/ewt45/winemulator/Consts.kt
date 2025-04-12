package org.github.ewt45.winemulator

import android.content.Context
import java.io.File

object Consts {
    lateinit var cacheDir:File
    lateinit var rootfsDir:File

    /**
     * 初始化。使用前先调用一次
     */
    fun init(ctx:Context) {
        cacheDir = ctx.cacheDir
        cacheDir.mkdirs()

        val fileDir = ctx.filesDir
        rootfsDir = File(fileDir, "rootfs")
        rootfsDir.mkdirs()
    }
}