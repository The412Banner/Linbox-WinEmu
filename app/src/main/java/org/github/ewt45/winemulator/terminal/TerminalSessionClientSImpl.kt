package org.github.ewt45.winemulator.terminal

import com.termux.terminal.TerminalSession

class TerminalSessionClientSImpl(val service: TerminalService):TerminalSessionClientBase() {
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        if (service.termuxSession?.terminalSession == session) {
            service.termuxSession!!.executionCommand.mPid = pid
        }
    }
}