package org.github.ewt45.winemulator.terminal

import a.io.github.ewt45.winemulator.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import org.github.ewt45.winemulator.Consts

class TerminalService : Service(), TermuxSession.TermuxSessionClient {
    private val TAG = "TerminalService"

    class LocalBinder(val service: TerminalService) : Binder()

    private val binder = LocalBinder(this)
    private var sessionClientA:TerminalSessionClientAImpl? = null
    private val sessionClientS: TerminalSessionClientSImpl = TerminalSessionClientSImpl(this)
    // 原本是在shellManager中的list. 改成只有一个吧
    var termuxSession: TermuxSession? = null


    override fun onCreate() {
        runStartForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runStartForeground()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        runStopForeground()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (sessionClientA != null)
            unsetTerminalSessionClientA();
        return false;
    }

    private fun runStartForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Consts.notificationChannelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH)
        )
        //PendingIntent先不管了
        val builder = Notification.Builder(this, Consts.notificationChannelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在运行")
            .setOngoing(true)
//            .setSmallIcon(R.drawable.)

        startForeground(Consts.notificationId, builder.build())
    }

    private fun runStopForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** 在connection.onServiceConnected 调用 */
    fun setTerminalSessionClientA(client: TerminalSessionClientAImpl):Unit  = synchronized(this) {
        sessionClientA = client
        termuxSession?.terminalSession?.updateTerminalSessionClient(client)
    }

    /** 在 service.unbind 和 activity.onDestroy 时调用 */
    fun unsetTerminalSessionClientA():Unit = synchronized(this) {
        termuxSession?.terminalSession?.updateTerminalSessionClient(sessionClientS)
        sessionClientA = null
        TODO("尚未实现")
    }

    private fun killExecutionCommand() = synchronized(this) {
        termuxSession?.killIfExecuting(this, true)
    }

    override fun onTermuxSessionExited(termuxSession: TermuxSession?) {
        if (termuxSession != null && termuxSession == this.termuxSession)
            this.termuxSession = null
    }

    fun isTermuxSessionsEmpty() = termuxSession == null
    fun createTermuxSession(): TermuxSession? {
        if (termuxSession != null) {
            Log.e(TAG, "createTermuxSession: 已经有session, 不再新建", )
            return null
        }
        val workingDir = filesDir.absolutePath
        val executionCommand = ExecutionCommand(TermuxShellManager.getNextShellId(), null, null, null,
            workingDir, Runner.TERMINAL_SESSION.name, false )
        executionCommand.setShellCommandShellEnvironment = true
        executionCommand.terminalTranscriptRows = TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS

        termuxSession = TermuxSession.execute(this, executionCommand, getTerminalSessionClient(),
            this, TermuxShellEnvironment(), null, false)
        return termuxSession!!
    }

    fun getTerminalSessionClient() = if (sessionClientA != null) sessionClientA!! else sessionClientS
}