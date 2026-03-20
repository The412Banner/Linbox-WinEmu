package com.example.terminaltest.ui.home

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.terminaltest.MainEmuActivity
import com.termux.view.TerminalView

class TerminalManager(activity: MainEmuActivity) {

    val sessionClientA = TerminalSessionClientAImpl(activity)
    val viewClient = TerminalViewClientImpl(activity, sessionClientA)
    lateinit var terminalView: TerminalView
    var service: TerminalService? = null
    val serviceConnection = object: ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? TerminalService.LocalBinder)?.service
            service?.setTerminalSessionClientA(sessionClientA)
            if (service!!.isTermuxSessionEmpty()) {
                sessionClientA.addNewSession()
            } else {
                sessionClientA.setCurrentSession(sessionClientA.getCurrentStoredSessionOrLast())
            }
            TODO("Not yet implemented")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.unsetTerminalSessionClientA()
            TODO("Not yet implemented")
        }

    }

    /** activity.onCreate时调用 */
    fun onCreate(a: MainEmuActivity) {
        with(a) {
            preferences = EmuAppSharedPreferences(this)
            terminalView = binding.terminalView
            terminalView.setTerminalViewClient(terminalManager.viewClient)
            terminalView.setTextSize((12 * resources.displayMetrics.density).toInt())
            val terminalServiceIntent = Intent(this, TerminalService::class.java)
            startService(terminalServiceIntent)
            bindService(terminalServiceIntent, terminalManager.serviceConnection ,0)

            TODO("还有sessionClient的onCreate, onStart等")
        }
    }

    fun onDestroy(a: MainEmuActivity) {
        service?.unsetTerminalSessionClientA()
        TODO("还有sessionClient的onCreate")
    }
}