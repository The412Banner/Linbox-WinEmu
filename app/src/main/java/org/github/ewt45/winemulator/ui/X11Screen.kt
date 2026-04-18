package org.github.ewt45.winemulator.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.termux.x11.input.InputStub
import com.termux.x11.input.RenderData
import kotlin.math.abs
import kotlin.math.roundToInt
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.inputcontrols.InputControlsManager
import org.github.ewt45.winemulator.inputcontrols.InputControlsView
import org.github.ewt45.winemulator.inputcontrols.X11InputSender
import org.github.ewt45.winemulator.inputcontrols.InputEventHandler
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import kotlinx.coroutines.delay

/**
 * X11 Screen composable that displays X11 content with virtual controls overlay
 *
 * This screen includes:
 * - X11 rendering content from LorieView
 * - Virtual controls overlay
 * - Expandable floating menu with independent popup windows for:
 *   - General settings (container language, shared folders, rootfs management)
 *   - Virtual keys settings (enable/disable, profile selection, layout editing)
 *   - X11 display settings (resolution, touch mode, orientation, scale)
 *
 * Input events from virtual controls are routed through the X11InputSender
 * to the X11 session via the LorieView JNI bridge.
 */
@Composable
fun X11Screen(
    x11Content: (Context) -> View,
    onNavigateToOthers: (Destination) -> Unit,
    onLorieViewReady: ((InputStub) -> Unit)? = null,
    settingVm: SettingViewModel? = null
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val x11InputSender = remember { X11InputSender() }
    val renderData = remember { RenderData() }
    val manager = remember { InputControlsManager(context) }

    // 状态变量
    var currentProfileId by remember { mutableStateOf(prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)) }
    var showTouchscreenControls by remember { mutableStateOf(prefs.getBoolean("show_touchscreen_controls", false)) }

    // 轮询监听 SharedPreferences 变化（后备同步机制）
    LaunchedEffect(Unit) {
        while (true) {
            val newShowControls = prefs.getBoolean("show_touchscreen_controls", false)
            val newProfileId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)

            if (newShowControls != showTouchscreenControls) {
                Log.d("X11Screen", "showTouchscreenControls changed: $showTouchscreenControls -> $newShowControls")
                showTouchscreenControls = newShowControls
            }
            if (newProfileId != currentProfileId) {
                Log.d("X11Screen", "currentProfileId changed: $currentProfileId -> $newProfileId")
                currentProfileId = newProfileId
            }
            delay(300)
        }
    }

    // InputEventHandler 使用 X11InputSender
    val inputEventHandler = remember {
        object : InputEventHandler {
            override fun onKeyEvent(keycode: Int, isDown: Boolean) {
                x11InputSender.sendEvdevKeyEvent(keycode, isDown)
            }
            override fun onPointerMove(dx: Int, dy: Int) {
                x11InputSender.sendMouseMotionEvent(dx, dy)
            }
            override fun onPointerButton(button: Int, isDown: Boolean) {
                x11InputSender.sendMouseButtonEvent(button, isDown)
            }
        }
    }

    // 只创建一次 InputControlsView，避免重建
    val inputControlsView = remember {
        InputControlsView(context, editMode = false).apply {
            this.inputEventHandler = inputEventHandler
        }
    }

    // 监听显示开关变化并实时更新视图
    LaunchedEffect(showTouchscreenControls) {
        inputControlsView.showTouchscreenControls = showTouchscreenControls
        Log.d("X11Screen", "Updated showTouchscreenControls: $showTouchscreenControls")
    }

    // 监听配置 ID 变化并实时更新视图
    LaunchedEffect(currentProfileId) {
        val newProfile = if (currentProfileId != 0) manager.getProfile(currentProfileId) else manager.getProfiles().firstOrNull()
        inputControlsView.setProfile(newProfile)
        Log.d("X11Screen", "Updated profile to: ${newProfile?.name}")
    }

    // 即时刷新函数（供悬浮弹窗回调使用）
    val refreshControlsImmediately = {
        val newShowControls = prefs.getBoolean("show_touchscreen_controls", false)
        val newProfileId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)

        var needRefresh = false
        if (newShowControls != showTouchscreenControls) {
            showTouchscreenControls = newShowControls
            needRefresh = true
        }
        if (newProfileId != currentProfileId) {
            currentProfileId = newProfileId
            needRefresh = true
        }

        // 如果状态没有变化，仍然强制刷新一次视图（保证一致性）
        if (!needRefresh) {
            inputControlsView.showTouchscreenControls = newShowControls
            val newProfile = if (newProfileId != 0) manager.getProfile(newProfileId) else manager.getProfiles().firstOrNull()
            inputControlsView.setProfile(newProfile)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // X11 渲染视图
        AndroidView(
            factory = { ctx ->
                val view = x11Content(ctx)
                try {
                    val lorieView = if (view is com.termux.x11.LorieView) {
                        view
                    } else {
                        findLorieView(view)
                    }
                    lorieView?.let {
                        x11InputSender.initialize(it)
                        renderData.scale = android.graphics.PointF(1f, 1f)
                        x11InputSender.renderData = renderData
                        onLorieViewReady?.invoke(it)
                        Log.d("X11Screen", "X11InputSender initialized with LorieView")
                    } ?: run {
                        Log.e("X11Screen", "Could not find LorieView in X11 content")
                    }
                } catch (e: Exception) {
                    Log.e("X11Screen", "Failed to initialize X11InputSender: ${e.message}", e)
                }
                view
            },
            modifier = Modifier.fillMaxSize()
        )

        // 虚拟按键覆盖层
        AndroidView(
            factory = { inputControlsView },
            modifier = Modifier.fillMaxSize()
        )

        val floatingPopupState = remember { FloatingPopupState() }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            ExpandableFloatingMenu(
                parentWidth = constraints.maxWidth.toFloat(),
                parentHeight = constraints.maxHeight.toFloat(),
                onMainMenuClick = { onNavigateToOthers(Destination.Terminal) },
                onGeneralSettingsClick = { floatingPopupState.showPopup(FloatingPopupType.GENERAL_SETTINGS) },
                onVirtualKeysClick = { floatingPopupState.showPopup(FloatingPopupType.VIRTUAL_KEYS_SETTINGS) },
                onX11SettingsClick = { floatingPopupState.showPopup(FloatingPopupType.X11_SETTINGS) }
            )
        }

        settingVm?.let { vm ->
            FloatingSettingsPopups(
                popupState = floatingPopupState,
                settingVm = vm,
                onVirtualKeysSettingsChanged = refreshControlsImmediately
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            x11InputSender.release()
        }
    }
}

/**
 * Recursively find LorieView in the view hierarchy
 */
private fun findLorieView(view: View): com.termux.x11.LorieView? {
    if (view is com.termux.x11.LorieView) {
        return view
    }
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findLorieView(child)
            if (result != null) {
                return result
            }
        }
    }
    return null
}

/**
 * Floating button implementation:
 * - Uses IconButton for click handling (onClick)
 * - Uses pointerInput for drag handling
 * Both do not interfere with each other
 */
@Composable
private fun MiniButton2(
    modifier: Modifier = Modifier,
    parentWidth: Float,
    parentHeight: Float,
    onExpand: () -> Unit
) {
    val density = LocalDensity.current
    val buttonSizePx = with(density) { Consts.Ui.minimizedIconSize.dp.toPx() }

    // Drag threshold: only considered dragging beyond this distance
    val dragThreshold = with(density) { 30.dp.toPx() }

    // Initial position (48dp from left edge, 100dp from top)
    val initialX = with(density) { 48.dp.toPx() }
    val initialY = with(density) { 100.dp.toPx() }

    // Use rememberSaveable to persist position, avoiding reset on recomposition
    var offsetX by rememberSaveable { mutableStateOf(initialX) }
    var offsetY by rememberSaveable { mutableStateOf(initialY) }

    // Track whether dragging has started
    var hasDragged by rememberSaveable { mutableStateOf(false) }
    // Store the initial press position
    var pressStartX by rememberSaveable { mutableFloatStateOf(0f) }
    var pressStartY by rememberSaveable { mutableFloatStateOf(0f) }

    // When parent size changes, ensure floating button stays within bounds
    LaunchedEffect(parentWidth, parentHeight, buttonSizePx) {
        if (parentWidth > 0 && parentHeight > 0) {
            offsetX = offsetX.coerceIn(0f, parentWidth - buttonSizePx)
            offsetY = offsetY.coerceIn(0f, parentHeight - buttonSizePx)
        }
    }

    // Use IconButton for click handling; its onClick doesn't conflict with pointerInput
    IconButton(
        onClick = {
            // Only triggers when not dragging
            if (!hasDragged) {
                onExpand()
            }
        },
        modifier = Modifier
            .size(Consts.Ui.minimizedIconSize.dp)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                // Handle drag gestures
                detectDragGestures(
                    onDragStart = { offset ->
                        pressStartX = offset.x
                        pressStartY = offset.y
                        hasDragged = false
                    },
                    onDragEnd = {
                        if (hasDragged) {
                            // First clamp position to bounds
                            offsetX = offsetX.coerceIn(0f, parentWidth - buttonSizePx)
                            offsetY = offsetY.coerceIn(0f, parentHeight - buttonSizePx)

                            // Snap to nearest edge
                            val halfWidth = buttonSizePx / 2
                            val newX = if (offsetX + halfWidth < parentWidth / 2) 0f else parentWidth - buttonSizePx
                            offsetX = newX
                            offsetY = offsetY.coerceIn(0f, parentHeight - buttonSizePx)
                        }
                        // Reset drag state
                        hasDragged = false
                    },
                    onDragCancel = {
                        hasDragged = false
                    },
                    onDrag = { change, dragAmount ->
                        // Calculate total drag distance (using change.position since dragAmount may be consumed)
                        val totalDragX = change.position.x - pressStartX
                        val totalDragY = change.position.y - pressStartY
                        val totalDistance = abs(totalDragX) + abs(totalDragY)

                        // Only considered dragging when exceeding threshold
                        if (totalDistance > dragThreshold) {
                            hasDragged = true
                        }

                        // If in drag mode, update position
                        if (hasDragged) {
                            change.consume()

                            // Use dragAmount as delta — the correct increment provided by detectDragGestures
                            offsetX = offsetX + dragAmount.x
                            offsetY = offsetY + dragAmount.y
                        }
                    }
                )
            }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_fullscreen),
            contentDescription = "Expand",
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small
                )
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Deprecated("No longer need to operate on View")
@Composable
private fun MiniButton(
    minimize: Boolean,
    onClick: () -> Unit,
) {
    val activity = LocalActivity.current
    val miniIconPx = (Consts.Ui.minimizedIconSize * LocalDensity.current.density).toInt()
    // Slightly change color when minimized, otherwise hard to see
    val colorSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val colorContent = MaterialTheme.colorScheme.onSurface
    val colors =
        if (!minimize) IconButtonDefaults.iconButtonColors()
        else IconButtonColors(colorSurface, colorContent, colorSurface, colorContent)
    // Remember minimized position. Restore to last position instead of default when minimizing again after fullscreen
    val margin = remember { mutableListOf(0, 100) }
    IconButton(
        onClick = {
            val view = activity?.findViewById<View>(R.id.compose_view) ?: return@IconButton
            val nextValue = !minimize
            view.apply {
                val lp = layoutParams as MarginLayoutParams
                lp.height = if (nextValue) miniIconPx else MATCH_PARENT
                lp.width = if (nextValue) miniIconPx else MATCH_PARENT
                lp.leftMargin = if (nextValue) margin[0] else 0
                lp.topMargin = if (nextValue) margin[1] else 0
                lp.rightMargin = 0
                lp.bottomMargin = 0
                requestLayout()
                if (nextValue)
                    view.post { view.snapToNearestEdgeHalfway() }
            }
            onClick()
        },
        modifier = Modifier
            .size(Consts.Ui.minimizedIconSize.dp)
            .pointerInput(minimize) {
                if (!minimize)
                    return@pointerInput
                val view = activity?.findViewById<View>(R.id.compose_view) ?: return@pointerInput
                detectDragGestures(
                    onDragEnd = { view.snapToNearestEdgeHalfway() }
                ) { change, dragAmount ->
                    change.consume()
                    val lp = view.layoutParams as MarginLayoutParams
                    lp.leftMargin += dragAmount.x.toInt()
                    lp.topMargin += dragAmount.y.toInt()
                    margin[0] = lp.leftMargin
                    margin[1] = lp.topMargin
                    view.requestLayout()
                }
            },
        colors = colors
    ) {
        Icon(
            painter = painterResource(if (minimize) R.drawable.ic_fullscreen else R.drawable.ic_hide),
            contentDescription = "Fullscreen/Minimize",
        )
    }
}


@Preview(widthDp = 300, heightDp = 500)
@Composable
fun X11ScreenPreview() {
    X11Screen(
        x11Content = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.GRAY) } },
        {},
        settingVm = null
    )
}