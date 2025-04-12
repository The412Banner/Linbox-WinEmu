package org.github.ewt45.winemulator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainViewModel:ViewModel() {
    private val _commandOutput = MutableStateFlow("正在执行命令...")
    val commandOutput = _commandOutput.asStateFlow()

    fun runProotCommand(context: Context) {
        viewModelScope.launch {
            val proot = File(context.filesDir, "proot").absolutePath
            val rootfs = File(context.filesDir, "alpine-rootfs") // 假设你已解压好了

            val process = ProcessBuilder(
                proot,
                "-R", rootfs.absolutePath,
                "/bin/sh", "-c", "ls -la /"
            )
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach {
                    output.appendLine(it)
                }
            }

            process.waitFor()
            _commandOutput.value = output.toString()
        }
    }

    fun setDebugInfo(str:String) {
        _commandOutput.value = str
    }
}