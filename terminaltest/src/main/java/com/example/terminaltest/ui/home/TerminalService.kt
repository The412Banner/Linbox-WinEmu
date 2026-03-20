package com.example.terminaltest.ui.home

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSession


class TerminalService : Service() {
    private val TAG = "TerminalService"
    class LocalBinder(val service: TerminalService) : Binder()


    private val binder = LocalBinder(this)
    private var sessionClientA:TerminalSessionClientAImpl? = null
    private val sessionClientS: TerminalSessionClientSImpl = TerminalSessionClientSImpl(this)
    var terminalSession: TerminalSession? = null
    var executionCommand: ExecutionCommand? = null

    val a:Nothing = TODO("实现onCreate等")

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }



    /** 在connection.onServiceConnected 调用 */
    @Synchronized
    fun setTerminalSessionClientA(client: TerminalSessionClientAImpl)  {
        sessionClientA = client
        terminalSession?.updateTerminalSessionClient(client)
    }

    /** 在 service.unbind 和 activity.onDestroy 时调用 */
    @Synchronized
    fun unsetTerminalSessionClientA() {
        terminalSession?.updateTerminalSessionClient(sessionClientS)
        sessionClientA = null
        TODO("尚未实现")
    }

    fun isTermuxSessionEmpty(): Boolean {
        return terminalSession == null
    }

    fun createTermuxSession(): TermuxSession? {
        if (terminalSession != null) {
            Log.e(TAG, "createTermuxSession: 已经有session, 不再新建", )
            return null
        }
        val workingDir = filesDir.absolutePath
        val executionCommand = ExecutionCommand(
            TermuxShellManager.getNextShellId(), null, null, null,
            workingDir, Runner.TERMINAL_SESSION.name, false )
        executionCommand.setShellCommandShellEnvironment = true
        executionCommand.terminalTranscriptRows = TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS

        termuxSession = TermuxSession.execute(this, executionCommand, getTerminalSessionClient(),
            this, TermuxShellEnvironment(), null, false)
        return termuxSession!!
        TODO("不要用TermuxShellManager 实现自己的TermuxSession")
    }

    fun getTerminalSessionClient() = if (sessionClientA != null) sessionClientA!! else sessionClientS


    @Deprecated("这里获取最后一个TermuxSession的逻辑就是返回列表中的最后一个TermuxSession, 如果列表就返回null。" +
            "现在只有一个session直接获取这个session就行了", replaceWith = ReplaceWith("terminalSession")
    )
    @Synchronized
    fun getLastTermuxSession(): TerminalSession? {
        return terminalSession
    }

    @Synchronized
    fun getTerminalSessionForHandle(sessionHandle: String): TerminalSession? {
        return terminalSession?.takeIf { it.mHandle == sessionHandle }
    }
}