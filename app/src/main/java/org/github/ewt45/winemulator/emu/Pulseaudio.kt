package org.github.ewt45.winemulator.emu

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.Utils.getPid
import java.io.BufferedReader
import java.io.InputStreamReader

// Inside Linux, set env var: PULSE_SERVER=tcp:127.0.0.1:4713
object Pulseaudio {
    private val TAG = "Pulseaudio"

    private fun buildProcess(): ProcessBuilder {
        return ProcessBuilder().apply {
            environment()["HOME"] = Consts.pulseHomeDir.absolutePath
            environment()["TMPDIR"] = Consts.tmpDir.absolutePath
            environment()["LD_LIBRARY_PATH"] = Consts.pulseDir.absolutePath
            // Switching the module from sles to aaudio fixes this issue
//                environment()["LD_PRELOAD"] = "/system/lib64/liblzma.so" // Samsung-specific issue?
//                environment()["LD_PRELOAD"] = "/system/lib64/libskcodec.so"

        }
            .directory(Consts.pulseDir)
            .redirectErrorStream(true)
    }

    fun stop() {
        buildProcess().command("./pulseaudio", "--kill").start().waitFor()
        //TODO  delete leftover .config and pulse-xxxx folders to prevent pa_pid_file_create() failed

    }

    suspend fun start():Int = withContext(Dispatchers.IO) {
        stop()

        val process = buildProcess()
            .command("./pulseaudio --start --exit-idle-time=-1 -n -F ./pulseaudio.conf --daemonize=true".split(" "))
            .start()
        launch {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "pulseaudio output: $line")
                }
            }
        }
        return@withContext process.getPid()
    }

    /**
     * Sending a signal directly to pulseaudio has no effect; `pacmd suspend 1` works instead.
     */
    fun pause() {
        buildProcess()
            .command("./pacmd", "suspend", "1")
            .start()
    }

    fun resume() {
        buildProcess()
            .command("./pacmd", "suspend", "0")
            .start()
    }
}