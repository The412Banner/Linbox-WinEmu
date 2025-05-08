package org.github.ewt45.winemulator

import a.io.github.ewt45.winemulator.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.termux.x11.MainActivity
import com.termux.x11.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.github.ewt45.winemulator.emu.Pulseaudio
import org.github.ewt45.winemulator.ui.MainScreen
import org.github.ewt45.winemulator.ui.theme.MainTheme
import org.github.ewt45.winemulator.viewmodel.MainViewModel
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import org.github.ewt45.winemulator.viewmodel.TerminalViewModel


class MainEmuActivity : MainActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val terminalViewModel: TerminalViewModel by viewModels()
    private val settingViewModel: SettingViewModel by viewModels()
    private lateinit var startX11Intent: Intent

    companion object {
        private val getInstanceRef = ::getInstance

        @JvmStatic
        val instance: MainEmuActivity
            get() = getInstanceRef() as MainEmuActivity
//        val instance: MainEmuActivity by lazy { getInstance() as MainEmuActivity }
    }

    fun getPref(): Prefs {
        return prefs
    }

    fun onChangePref() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //设置包名
        MainActivity.HOST_PKG_NAME = packageName
        super.onCreate(savedInstanceState)
        //偏好设置
        prefs.displayResolutionMode.put("custom")
        runBlocking { prefs.displayResolutionCustom.put(settingViewModel.generalFLow.first().resolution) }
        prefs.showAdditionalKbd.put(false) // 不显示底部按键
        prefs.fullscreen.put(true) // 全屏
        prefs.hideCutout.put(false) // 挖孔屏等，先不在该区域显示吧。

        startX11Intent = createStartX11Intent()

        //将composeView添加到原视图布局中
        //TODO wrap不生效
        val composeView = ComposeView(this).apply {
            id = R.id.compose_view
            setContent {
                MainTheme {
                    MainScreen()
                }
            }
        }
        val frame = findViewById<FrameLayout>(com.termux.x11.R.id.frame)
        frame.addView(composeView, FrameLayout.LayoutParams(-2, -2))
        enableEdgeToEdge()
//        setContent {
//            MainTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    MainScreen(modifier = Modifier.padding(innerPadding))
//                }
//            }
//        }

        lifecycleScope.launch {
            val result = viewModel.showBlockDialog("解压alpine rootfs") {
                Utils.Rootfs.ensureAlpineRootfs(this@MainEmuActivity)
            }
            if (result.isFailure) {
                result.exceptionOrNull()!!.printStackTrace()
                viewModel.showConfirmDialog("错误：解压alpine rootfs 失败！").run { finish() }
                return@launch
            }

            //等待x11启动完成
            viewModel.showBlockDialog("xserver启动中") {
                waitForXStarted()
            }

            Utils.Rootfs.makeCurrent(Consts.alpineRootfsDir)
            //这里还不能用state因为state第一次获取的是默认值而非datastore来的值
            terminalViewModel.startTerminal(settingViewModel.prootFlow.first().startupCmd)
            Pulseaudio.killAndRun()
        }

        //启动xserver
        startService(startX11Intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(startX11Intent)

        // 删除通知 从onPause改到onDestroy
        val notificationManager = getSystemService(NotificationManager::class.java)
        val mNotificationId = 7892
        for (notification in notificationManager.activeNotifications)
            if (notification.id == mNotificationId)
                notificationManager.cancel(mNotificationId)
    }

    /**
     * 等待xserver启动完成
     */
    private suspend fun waitForXStarted() {
        while (true) {
            if (isConnected()) break
            else delay(200)
        }
    }

    override fun buildNotification(): Notification {
        val channelName = this.resources.getString(R.string.app_name)
        val channel = NotificationChannel(channelName, channelName, NotificationManager.IMPORTANCE_HIGH)
        channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)  channel.setAllowBubbles(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val builder: NotificationCompat.Builder =
            (NotificationCompat.Builder(this, channelName)).setContentTitle(channelName)
                .setSmallIcon(R.mipmap.ic_launcher).setContentText("模拟器正在运行")
                .setOngoing(true).setPriority(NotificationCompat.PRIORITY_MAX)
                .setSilent(true).setShowWhen(false)
//                .setContentIntent(PendingIntent.getActivity(this, 0, Intent.makeMainActivity(componentName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                //.setColor(-10453621)
        return builder.build()
    }

    /**
     * 创建一个intent用于启动X11Service. 在intent放入数据：
     * timestamp：时间戳
     *
     */
    private fun createStartX11Intent(): Intent {
        return Intent(this, X11Service::class.java).apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
    }
}