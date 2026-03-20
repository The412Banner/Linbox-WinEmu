package com.example.terminaltest.ui.home

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.terminaltest.MainEmuActivity
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient


open class TerminalSessionClientBase : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
    }

    override fun onBell(session: TerminalSession) {
    }

    override fun onColorsChanged(session: TerminalSession) {
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
    }

    override fun getTerminalCursorStyle() = TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE

    override fun logError(tag: String?, message: String?) {
        Log.e(tag, message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag, message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag, message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag, message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag, message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag, message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag, "", e)
    }
}

open class TerminalViewClientBase: TerminalViewClient {
    override fun onScale(scale: Float)  = 1.0f

    override fun onSingleTapUp(e: MotionEvent?) {}

    override fun shouldBackButtonBeMappedToEscape() = false

    override fun shouldEnforceCharBasedInput() = false

    override fun shouldUseCtrlSpaceWorkaround() = false

    override fun isTerminalViewSelected() = true

    override fun copyModeChanged(copyMode: Boolean) { }

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?) = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false

    override fun onLongPress(event: MotionEvent?) = false

    override fun readControlKey() = false

    override fun readAltKey() = false

    override fun readShiftKey() = false

    override fun readFnKey() = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false

    override fun onEmulatorSet() { }

    override fun logError(tag: String?, message: String?) {
        Log.e(tag, message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag, message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag, message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag, message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag, message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag, message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag, "", e)
    }
}

class TerminalSessionClientAImpl(
    val activity: MainEmuActivity,
): TerminalSessionClientBase() {
    fun addNewSession() {
        val newSession = activity.terminalManager.service?.createTermuxSession()
            ?: return
        setCurrentSession(newSession)
    }

    fun setCurrentSession(session: TerminalSession?) {
        if (session == null) return
        activity.terminalManager.terminalView.attachSession(session)
    }

    /** The current session as stored or the last one if that does not exist. */
    fun getCurrentStoredSessionOrLast(): TerminalSession? {
        return getCurrentStoredSession()
            ?: activity.terminalManager.service?.terminalSession
    }

    private fun getCurrentStoredSession(): TerminalSession? {
        val sessionHandle: String = activity.preferences.getCurrentSession() ?: return null
        return activity.terminalManager.service?.getTerminalSessionForHandle(sessionHandle)
    }
}

class TerminalSessionClientSImpl(val service: TerminalService):TerminalSessionClientBase() {
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        if (service.terminalSession == session) {
            service.executionCommand!!.mPid = pid
        }
    }
}

class TerminalViewClientImpl(
    val activity: MainEmuActivity,
    val sessionClient: TerminalSessionClientAImpl,
): TerminalViewClientBase() {
}