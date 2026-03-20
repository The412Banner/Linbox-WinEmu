package org.github.ewt45.winemulator.emu.manager

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.terminal.TerminalSessionClientAImpl
import org.github.ewt45.winemulator.terminal.TerminalService
import org.github.ewt45.winemulator.terminal.TerminalViewClientImpl

class TerminalManager(activity:MainEmuActivity) {
    private val TAG = "TerminalManager"
    val sessionClientA: TerminalSessionClientAImpl = TerminalSessionClientAImpl(activity)
    val viewClient: TerminalViewClientImpl = TerminalViewClientImpl(activity, sessionClientA)
    var service: TerminalService? = null

    val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected: 连接到TerminalService")
            service = (binder as TerminalService.LocalBinder).service
            service!!.setTerminalSessionClientA(sessionClientA)
            if (service!!.isTermuxSessionsEmpty()) {
                //TODO termux在此处进行初始化（例如解压bootstrap）
                sessionClientA.addNewSession()
            } else {
                sessionClientA.setCurrentSession()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            activity.finish()
        }

    }
}