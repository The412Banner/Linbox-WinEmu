package org.github.ewt45.winemulator

import a.io.github.ewt45.winemulator.R
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.system.Os
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.termux.x11.CmdEntryPoint
import com.termux.x11.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.ui.theme.MainTheme

class MainEmuActivity : MainActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val terminalViewModel: TerminalViewModel by viewModels()
    private lateinit var startX11ServiceIntent: Intent

    var foldComposeView = false
    override fun onCreate(savedInstanceState: Bundle?) {
        //设置包名
        MainActivity.HOST_PKG_NAME = packageName
        super.onCreate(savedInstanceState)
        //偏好设置
        prefs.showAdditionalKbd.put(false) // 不显示底部按键
        prefs.fullscreen.put(true) // 全屏
        prefs.hideCutout.put(false) // 挖孔屏等，先不在该区域显示吧。

        Consts.init(this)

        //将composeView添加到原视图布局中
        val composeView = ComposeView(this).apply {
            id = R.id.compose_view
            setContent {
                MainTheme {
                    //TODO 这里设置了fillmax.。。 所以wrap不生效
                    //TODO tx11已经处理键盘高度变更了，这里应该不用innerPadding 否则
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
        val frame = findViewById<FrameLayout>(com.termux.x11.R.id.frame)
        frame.addView(composeView, FrameLayout.LayoutParams(-2, -2))
        //缩小/放大
        val foldBtn = Button(this).apply {
            text = if (foldComposeView) "放大" else "缩小"
            setOnClickListener { v ->
                foldComposeView = !foldComposeView
                text = if (foldComposeView) "放大" else "缩小"
                composeView.layoutParams.height = if (foldComposeView) WRAP_CONTENT else MATCH_PARENT
                composeView.layoutParams.width = if (foldComposeView) WRAP_CONTENT else MATCH_PARENT
                composeView.requestLayout()
//                composeView.layoutParams = composeView.layoutParams
            }
        }
        (frame.parent as ViewGroup).addView(foldBtn, FrameLayout.LayoutParams(-2, -2))

        enableEdgeToEdge()
//        setContent {
//            MainTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    MainScreen(modifier = Modifier.padding(innerPadding))
//                }
//            }
//        }

        lifecycleScope.launch {
            viewModel.showBlockDialog("解压alpine rootfs")
            try {
                Utils.Rootfs.ensureAlpineRootfs(this@MainEmuActivity)
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.showBlockDialog("错误：解压alpine rootfs 失败！")
                return@launch
            }

            viewModel.closeBlockDialog()

            Utils.Rootfs.makeCurrent(Consts.alpineRootfsDir)
            terminalViewModel.startTerminal()
        }

        //启动xserver
//        lifecycleScope.launch(Dispatchers.IO) {
//            Os.setenv("TERMUX_X11_OVERRIDE_PACKAGE", packageName, true)
//            Os.setenv("TMPDIR", Consts.rootfsCurrDir.absolutePath + "/tmp", true)
//            Looper.prepare()
//            CmdEntryPoint.main(arrayOf(":1"))
//        }
        startX11ServiceIntent = Intent(this, X11Service::class.java)
        startService(startX11ServiceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(startX11ServiceIntent)
    }
}