package org.github.ewt45.winemulator.inputcontrols

import android.view.KeyEvent
import org.github.ewt45.winemulator.inputcontrols.ControlElement.Range

/**
 * Handles scrolling for range button elements
 * 范围按钮的滚动处理，用于RANGE_BUTTON类型元素
 */
class RangeScroller(
    private val inputControlsView: InputControlsView,
    private val element: ControlElement
) {
    private var scrollOffset: Float = 0f
    private val rangeIndex = intArrayOf(0, 26) // Start and end indices
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging = false
    private var lastActivatedIndex: Int = -1

    fun getElementSize(): Float {
        return inputControlsView.snappingSize * 4f * element.scale
    }

    fun getScrollOffset(): Float = scrollOffset

    fun getRangeIndex(): IntArray = rangeIndex

    fun handleTouchDown(element: ControlElement, x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
        isDragging = true
        lastActivatedIndex = -1
        updateRangeIndex(element, x, y)
    }

    fun handleTouchMove(element: ControlElement, x: Float, y: Float) {
        if (!isDragging) return

        val delta: Float = if (element.orientation == 0.toByte()) {
            x - lastTouchX
        } else {
            y - lastTouchY
        }

        scrollOffset -= delta
        lastTouchX = x
        lastTouchY = y

        updateRangeIndex(element, x, y)
        inputControlsView.invalidate()
    }

    fun handleTouchUp() {
        // Release any held binding
        if (lastActivatedIndex >= 0) {
            val keycode = getKeycodeForRangeIndex(element.range, lastActivatedIndex)
            if (keycode != 0) {
                inputControlsView.handleInputEvent(keycode, false)
            }
            lastActivatedIndex = -1
        }
        isDragging = false
    }

    /**
     * 根据范围类型和索引获取对应的Android KeyCode
     * Converts a range index to the corresponding Android keycode
     */
    private fun getKeycodeForRangeIndex(range: Range?, index: Int): Int {
        val currentRange = range ?: Range.FROM_A_TO_Z
        return when (currentRange) {
            Range.FROM_A_TO_Z -> {
                // A-Z: index 0-25 对应 KeyEvent.KEYCODE_A 到 KeyEvent.KEYCODE_Z
                when (index) {
                    0 -> KeyEvent.KEYCODE_A
                    1 -> KeyEvent.KEYCODE_B
                    2 -> KeyEvent.KEYCODE_C
                    3 -> KeyEvent.KEYCODE_D
                    4 -> KeyEvent.KEYCODE_E
                    5 -> KeyEvent.KEYCODE_F
                    6 -> KeyEvent.KEYCODE_G
                    7 -> KeyEvent.KEYCODE_H
                    8 -> KeyEvent.KEYCODE_I
                    9 -> KeyEvent.KEYCODE_J
                    10 -> KeyEvent.KEYCODE_K
                    11 -> KeyEvent.KEYCODE_L
                    12 -> KeyEvent.KEYCODE_M
                    13 -> KeyEvent.KEYCODE_N
                    14 -> KeyEvent.KEYCODE_O
                    15 -> KeyEvent.KEYCODE_P
                    16 -> KeyEvent.KEYCODE_Q
                    17 -> KeyEvent.KEYCODE_R
                    18 -> KeyEvent.KEYCODE_S
                    19 -> KeyEvent.KEYCODE_T
                    20 -> KeyEvent.KEYCODE_U
                    21 -> KeyEvent.KEYCODE_V
                    22 -> KeyEvent.KEYCODE_W
                    23 -> KeyEvent.KEYCODE_X
                    24 -> KeyEvent.KEYCODE_Y
                    25 -> KeyEvent.KEYCODE_Z
                    else -> 0
                }
            }
            Range.DIGITS -> {
                // 0-9: index 0-9 对应 KeyEvent.KEYCODE_0 到 KeyEvent.KEYCODE_9
                when (index) {
                    0 -> KeyEvent.KEYCODE_0
                    1 -> KeyEvent.KEYCODE_1
                    2 -> KeyEvent.KEYCODE_2
                    3 -> KeyEvent.KEYCODE_3
                    4 -> KeyEvent.KEYCODE_4
                    5 -> KeyEvent.KEYCODE_5
                    6 -> KeyEvent.KEYCODE_6
                    7 -> KeyEvent.KEYCODE_7
                    8 -> KeyEvent.KEYCODE_8
                    9 -> KeyEvent.KEYCODE_9
                    else -> 0
                }
            }
            Range.FUNCTION_KEYS -> {
                // F1-F12: index 0-11 对应 KeyEvent.KEYCODE_F1 到 KeyEvent.KEYCODE_F12
                when (index) {
                    0 -> KeyEvent.KEYCODE_F1
                    1 -> KeyEvent.KEYCODE_F2
                    2 -> KeyEvent.KEYCODE_F3
                    3 -> KeyEvent.KEYCODE_F4
                    4 -> KeyEvent.KEYCODE_F5
                    5 -> KeyEvent.KEYCODE_F6
                    6 -> KeyEvent.KEYCODE_F7
                    7 -> KeyEvent.KEYCODE_F8
                    8 -> KeyEvent.KEYCODE_F9
                    9 -> KeyEvent.KEYCODE_F10
                    10 -> KeyEvent.KEYCODE_F11
                    11 -> KeyEvent.KEYCODE_F12
                    else -> 0
                }
            }
            Range.NUMPAD_DIGITS -> {
                // NP0-NP9: index 0-9 对应 KeyEvent.KEYCODE_NUMPAD_0 到 KeyEvent.KEYCODE_NUMPAD_9
                when (index) {
                    0 -> KeyEvent.KEYCODE_NUMPAD_0
                    1 -> KeyEvent.KEYCODE_NUMPAD_1
                    2 -> KeyEvent.KEYCODE_NUMPAD_2
                    3 -> KeyEvent.KEYCODE_NUMPAD_3
                    4 -> KeyEvent.KEYCODE_NUMPAD_4
                    5 -> KeyEvent.KEYCODE_NUMPAD_5
                    6 -> KeyEvent.KEYCODE_NUMPAD_6
                    7 -> KeyEvent.KEYCODE_NUMPAD_7
                    8 -> KeyEvent.KEYCODE_NUMPAD_8
                    9 -> KeyEvent.KEYCODE_NUMPAD_9
                    else -> 0
                }
            }
        }
    }

    private fun updateRangeIndex(element: ControlElement, x: Float, y: Float) {
        val range = element.range ?: Range.FROM_A_TO_Z
        val elementSize = getElementSize()
        val box = element.getBoundingBox()

        val position: Float = if (element.orientation == 0.toByte()) {
            (x - box.left + scrollOffset) / elementSize
        } else {
            (y - box.top + scrollOffset) / elementSize
        }

        // Calculate visible range (show about 4-5 items)
        val visibleCount = 5
        val centerIndex = position.toInt().coerceIn(0, (range.max - 1).toInt())
        val startIndex = (centerIndex - visibleCount / 2).coerceAtLeast(0)
        val endIndex = minOf(startIndex + visibleCount, range.max.toInt())

        rangeIndex[0] = startIndex
        rangeIndex[1] = endIndex

        // Trigger the binding for the center index
        if (centerIndex != lastActivatedIndex) {
            // Release previous binding
            if (lastActivatedIndex >= 0) {
                val prevKeycode = getKeycodeForRangeIndex(range, lastActivatedIndex)
                if (prevKeycode != 0) {
                    inputControlsView.handleInputEvent(prevKeycode, false)
                }
            }

            // Activate new binding - 直接发送按键事件而不是通过bindings数组
            val keycode = getKeycodeForRangeIndex(range, centerIndex)
            if (keycode != 0) {
                inputControlsView.handleInputEvent(keycode, true)
                lastActivatedIndex = centerIndex
            }
        }
    }
}
