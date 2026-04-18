package org.github.ewt45.winemulator.viewmodel

import android.system.OsConstants.SIGCONT
import android.system.OsConstants.SIGSTOP
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Utils.getPid
import org.github.ewt45.winemulator.emu.Proot
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class TerminalViewModel : ViewModel() {
    private val TAG = "TerminalViewModel"
    private val terminal: Proot = Proot()
    private var process: Process? = null

    /** stdin writer */
    private var processWriter: OutputStreamWriter? = null

    /** Output lines. Each string represents one line; newline characters are included at the end — do not append additional newlines when joining. */
    private val _output = mutableStateOf<List<String>>(emptyList())
    val output get() = _output

    private val outputMutex = Mutex() // lock for all output-related mutations

    var currentUser by mutableStateOf("root")
        private set

    var currentHost by mutableStateOf("localhost")
        private set

    var currentPath by mutableStateOf("~")
        private set

    var isConnected by mutableStateOf(false)
        private set

    private val rootUserColor = Color(0xFFE0E0E0)   // root user: white
    private val normalUserColor = Color(0xFF64B5F6) // normal user: blue
    private val hostColor = Color(0xFF4DD0E1)        // hostname: cyan
    private val pathColor = Color(0xFF81C784)        // path: green
    private val symbolColor = Color(0xFFFFD54F)      // symbols: yellow

    /** Formatted prompt: user@host:path$ */
    val promptPrefix: String
        get() = "$currentUser@$currentHost:$currentPath$ "
    
    /**
     * Returns a colored prompt as an AnnotatedString.
     * Root is shown in white; all other users in blue.
     */
    fun getColoredPrompt(): AnnotatedString {
        val userColor = if (currentUser == "root") rootUserColor else normalUserColor

        return buildAnnotatedString {
            withStyle(SpanStyle(color = userColor, fontWeight = FontWeight.Bold)) {
                append(currentUser)
            }
            withStyle(SpanStyle(color = symbolColor)) { append("@") }
            withStyle(SpanStyle(color = hostColor, fontWeight = FontWeight.Bold)) {
                append(currentHost)
            }
            withStyle(SpanStyle(color = symbolColor)) { append(":") }
            withStyle(SpanStyle(color = pathColor, fontWeight = FontWeight.Bold)) {
                append(currentPath)
            }
            withStyle(SpanStyle(color = symbolColor)) {
                append(if (currentUser == "root") "# " else "$ ")
            }
        }
    }

    /**
     * Starts the terminal process.
     */
    suspend fun startTerminal() {
        if (process != null) return
        isConnected = true

        process = withContext(Dispatchers.IO) {
            terminal.attach().start()
        }

        processWriter = OutputStreamWriter(process!!.outputStream)

        // Launch a coroutine to read output and wait for the process to exit
        viewModelScope.launch(Dispatchers.IO) {
            updateOutput("Terminal connected")
            updateOutput("---")
            try {
                BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                    val builder = StringBuilder()
                    var readInt: Int
                    var charRead: Char
                    var lastReadCharTime = 0L // time the last char was read; updated on every char even without a full line
                    // builder, lastReadCharTime, and output must be accessed under the lock

                    // FIXME last adduser confirmation line not showing up?
                    val updateInlineOutputJob = launch {
                        var lastReadCharTimeCopy = 0L
                        while (process?.isAlive == true) {
                            delay(500)
                            outputMutex.withLock {
                                // If no new output arrived in the last 500 ms, flush the buffered partial line to screen.
                                if (lastReadCharTime == lastReadCharTimeCopy && lastReadCharTimeCopy != 0L && builder.isNotEmpty()) {
                                    val currentList = _output.value
                                    val lastLine = if (currentList.isNotEmpty()) currentList[currentList.lastIndex] else null
                                    if (lastLine?.endsWith('\n') != false) updateOutput(builder.toString())
                                    else updateOutputAtLast(builder.toString())
                                    builder.clear()
                                }
                                lastReadCharTimeCopy = lastReadCharTime
                            }
                        }
                    }

                    while (reader.read().also { readInt = it } != -1) {
                        var charRead = readInt.toChar()
                        var skipChar = false
                        
                        // Normalize CR: treat \r\n or lone \r as a newline
                        if (charRead == '\r') {
                            val nextInt = reader.read()
                            if (nextInt != -1) {
                                val nextChar = nextInt.toChar()
                                if (nextChar == '\n') {
                                    // \r\n — discard the \r and let \n be handled normally next iteration
                                    charRead = nextChar
                                } else {
                                    // lone \r — treat as newline
                                    val line = builder.toString()
                                    detectUserChange(line)
                                    val currentList = _output.value.toMutableList()
                                    if (currentList.size > 800) {
                                        currentList.removeAt(0)
                                    }
                                    currentList.add(line)
                                    _output.value = currentList
                                    builder.clear()
                                    // Start the next line with the char we already read
                                    builder.append(nextChar)
                                    skipChar = true
                                }
                            }
                        }
                        
                        if (!skipChar) {
                            outputMutex.withLock {
                                lastReadCharTime = System.currentTimeMillis()
                                builder.append(charRead)

                                // Attempt to parse the current path (simple heuristic)
                                if (charRead == ':' && builder.length > 2) {
                                    val potentialPath = builder.toString().takeLast(50)
                                    if (potentialPath.matches(Regex(""".*:[/~][/\w.-]*\$?"""))) {
                                        currentPath = extractPath(potentialPath)
                                    }
                                }

                                if (charRead == '\n') {
                                    val line = builder.toString()
                                    detectUserChange(line)

                                    // Cap output lines
                                    val currentList = _output.value.toMutableList()
                                    if (currentList.size > 800) {
                                        currentList.removeAt(0)
                                    }
                                    currentList.add(line)
                                    _output.value = currentList
                                    builder.clear()
                                }
                            }
                        }
                    }
                    updateInlineOutputJob.cancel()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateOutput("Error: ${e.message}")
            }
            process?.waitFor()
            isConnected = false
            closeResources()
        }

        if (Proot.lastTimeCmd.isNotBlank()) {
            updateOutput("Starting proot with arguments:")
            updateOutput(Proot.lastTimeCmd)
            updateOutput("")
        }
        return
    }

    /**
     * Extracts the current path from a prompt line.
     */
    private fun extractPath(text: String): String {
        // Match a path starting with ~ or /
        val pathMatch = Regex(""":([/~][/\w.-]*)[\$#]""").find(text)
        return pathMatch?.groupValues?.get(1) ?: "~"
    }

    /**
     * Detects whether the current user has changed based on output lines.
     */
    private fun detectUserChange(line: String) {
        // Detect user switch after commands like `su - username` or `sudo -i`
        if (line.contains("su -") || line.contains("sudo -i")) {
            val userMatch = Regex("""su\s+-\s*(\w+)""").find(line)
                ?: Regex("""sudo\s+-i""").find(line)
            // Simplified: assume switch to root
            if (userMatch != null) {
                currentUser = "root"
            }
        }
        // Detect `whoami` output
        if (line.contains("root") && _output.value.size > 5) {
            // Check if a recent line contained the `whoami` command
            val recentLines = _output.value.takeLast(5)
            if (recentLines.any { it.contains("whoami") }) {
                currentUser = "root"
            }
        }
    }

    /** Appends a line to the output list. */
    private fun updateOutput(line: String) {
        val currentList = _output.value.toMutableList()
        currentList.add(line)
        _output.value = currentList
    }

    /** Appends [additional] to the last line of the output list. */
    private fun updateOutputAtLast(additional: String) {
        val currentList = _output.value.toMutableList()
        if (currentList.isNotEmpty()) {
            val lastIndex = currentList.lastIndex
            currentList[lastIndex] = currentList[lastIndex] + additional
            _output.value = currentList
        } else {
            currentList.add(additional)
            _output.value = currentList
        }
    }

    /**
     * Sends [command] to the terminal process.
     * @param display if false, the command is not echoed to the output view
     */
    fun runCommand(command: String, display: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {

        if (processWriter == null || process?.isAlive != true) {
            updateOutput("Process closed. Cannot execute command $command")
            stopTerminal()
            return@launch
        }

        if (display) {
            updateOutput(promptPrefix + command)
        }

        try {
            processWriter?.write(command + "\n") // newline required to execute
            processWriter?.flush()               // send immediately
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Updates the prompt's username (read from settings).
     */
    fun updatePromptFromSettings(userName: String) {
        currentUser = userName.ifBlank { "root" }
    }

    /** Stops the terminal process. */
    fun stopTerminal() {
        isConnected = false
        closeResources()
    }

    /** Closes all process streams and releases resources. */
    private fun closeResources() {
        try {
            processWriter?.close()
            process?.outputStream?.close()
            process?.inputStream?.close()
            process?.errorStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            process?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        process = null
        processWriter = null
        isConnected = false
    }


    /** Stops the terminal when the ViewModel is destroyed. */
    override fun onCleared() {
        super.onCleared()
        stopTerminal()
    }

    fun pauseTerminal() {
        val pid = process?.getPid() ?: -1
        android.os.Process.sendSignal(pid, SIGSTOP)
    }

    fun resumeTerminal() {
        val pid = process?.getPid() ?: -1
        android.os.Process.sendSignal(pid, SIGCONT)
    }
}
