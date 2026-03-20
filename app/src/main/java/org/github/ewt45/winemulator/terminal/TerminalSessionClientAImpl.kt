package org.github.ewt45.winemulator.terminal

import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSession
import org.github.ewt45.winemulator.MainEmuActivity

/**
 * activity的session client
 */
class TerminalSessionClientAImpl(
    val activity: MainEmuActivity,
): TerminalSessionClientBase() {
    fun addNewSession() {
        val newSession = activity.terminalManager.service?.createTermuxSession()
            ?: return
        setCurrentSession(newSession.terminalSession)
    }

    fun setCurrentSession(session: TerminalSession?) {
        if (session == null) return
        activity.terminalView.attachSession(session)
    }

}