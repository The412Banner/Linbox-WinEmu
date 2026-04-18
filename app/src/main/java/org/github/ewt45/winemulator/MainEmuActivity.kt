package org.github.ewt45.winemulator

import a.io.github.ewt45.winemulator.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.termux.x11.MainActivity
import com.termux.x11.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.github.ewt45.winemulator.Consts.Pref.general_rootfs_lang
import org.github.ewt45.winemulator.Consts.Pref.proot_startup_cmd
import org.github.ewt45.winemulator.Utils.activityRecreate
import org.github.ewt45.winemulator.Utils.getX11ServicePid
import org.github.ewt45.winemulator.emu.X11Service
import org.github.ewt45.winemulator.emu.manager.EmuManager
import org.github.ewt45.winemulator.terminal.SessionClientAImpl
import org.github.ewt45.winemulator.terminal.ViewClientImpl
import org.github.ewt45.winemulator.ui.Destination
import org.github.ewt45.winemulator.ui.MainScreen
import org.github.ewt45.winemulator.ui.theme.MainTheme
import org.github.ewt45.winemulator.viewmodel.MainViewModel
import org.github.ewt45.winemulator.viewmodel.PrepareViewModel
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import org.github.ewt45.winemulator.viewmodel.TerminalViewModel


class MainEmuActivity : MainActivity() {
    private val TAG = "MainEmuActivity"
    val mainViewModel: MainViewModel by viewModels()
    val terminalViewModel: TerminalViewModel by viewModels()
    val settingViewModel: SettingViewModel by viewModels()
    val prepareViewModel: PrepareViewModel by viewModels()
    private lateinit var startX11Intent: Intent
    private var emuStarted: Boolean = false
    val sessionClient: SessionClientAImpl = SessionClientAImpl(this)
    val viewClient: ViewClientImpl = ViewClientImpl(this, sessionClient)

    companion object {
        val instance get() = getInstance() as MainEmuActivity // val instance: MainEmuActivity by lazy { getInstance() as MainEmuActivity }
    }

    fun getPref(): Prefs = prefs

    init {
        Utils.Permissions.registerForActivityResult(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.activityRecreate = true
        Log.d(TAG, "onSaveInstanceState: saving state")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState?.activityRecreate == true) {
            Log.e(TAG, "onCreate: activity is being recreated — may need special handling")
        }

        // Set package name
        MainActivity.HOST_PKG_NAME = packageName
        startX11Intent = createStartX11Intent()
        super.onCreate(savedInstanceState)

        // Initialize X11 settings SharedPreferences sync
        settingViewModel.initSharedPreferences(this)

        // Sync all X11 settings to SharedPreferences on startup
        settingViewModel.syncX11SettingsToSharedPrefs()

        // Preferences
        prefs.displayResolutionMode.put("custom")
        runBlocking { prefs.displayResolutionCustom.put(Consts.Pref.general_resolution.get()) }
        prefs.showAdditionalKbd.put(false) // hide the bottom keyboard
        // Fullscreen setting
        runBlocking { prefs.fullscreen.put(Consts.Pref.x11_fullscreen.get()) }
        // Notch/cutout setting
        prefs.hideCutout.put(false)


//        // Add ComposeView to the original layout
//        val composeView = ComposeView(this).apply {
//            id = R.id.compose_view
//            setContent {
//                MainTheme {
//                    MainScreen()
//                }
//            }
//        }
//        val frame = findViewById<FrameLayout>(com.termux.x11.R.id.frame)
//        frame.addView(composeView, FrameLayout.LayoutParams(-2, -2))

        // Embed the original view inside Compose
        setContent {
            // Fetch and apply theme setting
            val themeMode by settingViewModel.themeState.collectAsState()
            val isDarkTheme = themeMode != 0 // 0 = follow system

            MainTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    tx11Content = { frm.also { (frm.parent as? ViewGroup)?.removeView(frm) } },
                    Destination.X11, mainViewModel, terminalViewModel, settingViewModel, prepareViewModel
                )
            }
        }

        // Auto-start emulator once preparation finishes
        lifecycleScope.launch {
            // Observe preparation state
            prepareViewModel.uiState.collect { state ->
                if (state.isPrepareFinished && !emuStarted) {
                    // Preparation done and emulator not yet started — launch it
                    lifecycleScope.launch {
                        startEmu()
                    }
                }
            }
        }

        enableEdgeToEdge()

//            startEmu()
//
//            // try termux terminal
    }

    suspend fun startEmu() = withContext(Dispatchers.Default) {
        if (emuStarted) {
            Log.w(TAG, "startEmu: emuStarted is true — emulator already running, skipping")
            return@withContext
        }
        // TODO: would switching to an IO coroutine here be better?
//        lifecycleScope.launch {
//            Log.d(TAG, "prepareAndStart: test process output? ${Utils.readLinesProcessOutput(Runtime.getRuntime().exec(arrayOf("sh",
//                "-c",
//                "umask 0022 ; ls /storage/emulated/0", // sh -c expects a single string — do not split
//                )))}")

        val selectedRootfs = Utils.Rootfs.getSelectedRootfs()!!
        // rootfs setup (external storage binding is handled inside Proot)
        Utils.Rootfs.makeCurrent(selectedRootfs)

        emuStarted = true

        // Fetch the login username from settings and update TerminalViewModel before starting the terminal.
        // runBlocking ensures the username is set before startTerminal is called.
        runBlocking {
            val userName = settingViewModel.getCurrentLoginUser()
            terminalViewModel.updatePromptFromSettings(userName)
            Log.d(TAG, "startEmu: fetched login user: $userName")
        }

        // Start xserver
        if (Consts.rootfsCurrXkbDir.exists()) {
            startService(startX11Intent)
            waitForXStartedWithDialog() // wait for X11 to finish starting
        } else {
            mainViewModel.showConfirmDialog("Missing xkb folder in rootfs — x11 will not start. Install a package like libxkbcommon-x11 to fix this.")
        }

        terminalViewModel.startTerminal()
        // TODO: once everything is moved into EmuManager, add the observer in init but start manually in startEmu rather than in onCreate
        // Adding an observer immediately replays all states from the beginning, so onCreate fires immediately
        withContext(Dispatchers.Main) {
            lifecycle.addObserver(EmuManager(lifecycleScope))
        }
        val LANG = general_rootfs_lang.get()
        // Run locale-gen if the target locale has not been generated yet
        val langBase = LANG.substringBefore('.')  // e.g. "zh_CN.utf8" -> "zh_CN"
        terminalViewModel.runCommand("""if ! locale -a | grep -qi "$langBase"; then locale-gen $LANG; fi; export LANG=$LANG""")
        // Can't use state here yet: the first value from state is the default, not the DataStore value
        proot_startup_cmd.get().takeIf { it.isNotBlank() }?.let {
            terminalViewModel.runCommand("$it &")
        }


//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalViewModel.stopTerminal()
        stopService(startX11Intent)
        // FIXME: in release builds, finish() does not kill the service process, causing xserver to fail on next launch — kill it manually
        android.os.Process.killProcess(getX11ServicePid())

        // Cancel notification (moved from onPause to onDestroy)
        val notificationManager = getSystemService(NotificationManager::class.java)
        val mNotificationId = 7892
        for (notification in notificationManager.activeNotifications)
            if (notification.id == mNotificationId)
                notificationManager.cancel(mNotificationId)
    }

    /** Waits for xserver to start, up to 5 seconds. */
    suspend fun waitForXStarted() {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 5000) {
            if (isConnected()) break
            else delay(200)
        }
    }

    suspend fun waitForXStartedWithDialog() {
        mainViewModel.showBlockDialog("Starting xserver") {
            waitForXStarted()
        }
    }

    override fun buildNotification(): Notification {
        val channelName = this.resources.getString(R.string.app_name)
        val channel = NotificationChannel(channelName, channelName, NotificationManager.IMPORTANCE_HIGH)
        channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) channel.setAllowBubbles(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val builder: NotificationCompat.Builder =
            (NotificationCompat.Builder(this, channelName)).setContentTitle(channelName)
                .setSmallIcon(R.mipmap.ic_launcher).setContentText("Emulator is running")
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                .setSilent(true).setShowWhen(false)
//                .setContentIntent(PendingIntent.getActivity(this, 0, Intent.makeMainActivity(componentName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        //.setColor(-10453621)
        return builder.build()
    }

    /** Creates an Intent for starting X11Service with a timestamp extra. */
    private fun createStartX11Intent(): Intent {
        return Intent(this, X11Service::class.java).apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
    }
}
