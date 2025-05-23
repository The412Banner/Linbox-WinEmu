package org.github.ewt45.winemulator.ui

import android.view.LayoutInflater
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import org.github.ewt45.winemulator.MainEmuActivity

/**
 * 使用termux的TerminalView显示终端和交互
 */
@Composable
fun TerminalScreen() {
    TerminalScreenImpl()
}

@Composable
private fun TerminalScreenImpl() {
    val activity = LocalActivity.current as MainEmuActivity
    Column(
        Modifier.fillMaxSize(),
    ) {
        /*
        termux中使用TerminalView的xml布局
        <com.termux.view.TerminalView
            android:id="@+id/terminal_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:defaultFocusHighlightEnabled="false"
            android:focusableInTouchMode="true"
            android:scrollbarThumbVertical="@drawable/terminal_scroll_shape"
            android:scrollbars="vertical"
            tools:ignore="UnusedAttribute" />
         */
        AndroidView({ ctx ->
            TerminalView(ctx, null).apply {
                setTerminalViewClient(activity.viewClient)
                isFocusableInTouchMode = true
                isVerticalScrollBarEnabled = true
            }
        })
    }
}

@Preview
@Composable
fun TerminalScreenPreview() {
    TerminalScreenImpl()
}