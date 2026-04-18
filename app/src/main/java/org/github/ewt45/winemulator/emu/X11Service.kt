package org.github.ewt45.winemulator.emu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.termux.x11.CmdEntryPoint
import com.termux.x11.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.MainEmuActivity
import a.io.github.ewt45.winemulator.R

class X11Service : LifecycleService() {
    private val TAG = "X11Service"
    private var started = false
    var job: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "X11ServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "X11 Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "X11 Server Service"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainEmuActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WinEmulator")
            .setContentText("X11 Server Running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: called")

        // Start foreground service to prevent it from being killed
        startForeground(NOTIFICATION_ID, createNotification())

        // Read settings only from the incoming Intent; other sources may be unreliable inside a service
        if (!started) {
            val timestamp = intent?.getLongExtra("timestamp", -1)
            val xkbDir = Consts.rootfsCurrXkbDir
            val tmpDir = Consts.tmpDir
            if (!xkbDir.exists() || !tmpDir.exists()) {
                Log.e(TAG, "onStartCommand: required directories (xkb or tmp) missing — not starting xserver")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_STICKY
            }
//            Os.setenv("TERMUX_X11_DEBUG", "1", true)
            Os.setenv("TERMUX_X11_OVERRIDE_PACKAGE", packageName, true)
            Os.setenv("TMPDIR", tmpDir.absolutePath, true)
            Os.setenv("XKB_CONFIG_ROOT", xkbDir.absolutePath, true)
            MainActivity.HOST_PKG_NAME = packageName

            started = true
            job = lifecycleScope.launch(Dispatchers.IO) {
                Looper.prepare() // required for some reason
                CmdEntryPoint.main(arrayOf(":13")) // -xstartup exits as soon as the command finishes, so it can't be used here
                Log.d(TAG, "onStartCommand: x11 process ended — stopping service")
                started = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel("service onDestroy: stopping xserver")
        started = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}