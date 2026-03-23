package org.github.ewt45.winemulator.inputcontrols

import org.github.ewt45.winemulator.inputcontrols.ControlElement.Range

/**
 * Handles scrolling for range button elements
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
            val binding = getBindingForRangeIndex(element.range, lastActivatedIndex)
            if (binding != Binding.NONE) {
                inputControlsView.handleInputEvent(binding, false)
            }
            lastActivatedIndex = -1
        }
        isDragging = false
    }

    /**
     * 根据范围类型和索引获取对应的Binding
     * Converts a range index to the corresponding Binding
     */
    private fun getBindingForRangeIndex(range: Range?, index: Int): Binding {
        val currentRange = range ?: Range.FROM_A_TO_Z
        return when (currentRange) {
            Range.FROM_A_TO_Z -> {
                // A-Z: index 0-25 对应 Binding.KEY_A 到 Binding.KEY_Z
                when (index) {
                    0 -> Binding.KEY_A
                    1 -> Binding.KEY_B
                    2 -> Binding.KEY_C
                    3 -> Binding.KEY_D
                    4 -> Binding.KEY_E
                    5 -> Binding.KEY_F
                    6 -> Binding.KEY_G
                    7 -> Binding.KEY_H
                    8 -> Binding.KEY_I
                    9 -> Binding.KEY_J
                    10 -> Binding.KEY_K
                    11 -> Binding.KEY_L
                    12 -> Binding.KEY_M
                    13 -> Binding.KEY_N
                    14 -> Binding.KEY_O
                    15 -> Binding.KEY_P
                    16 -> Binding.KEY_Q
                    17 -> Binding.KEY_R
                    18 -> Binding.KEY_S
                    19 -> Binding.KEY_T
                    20 -> Binding.KEY_U
                    21 -> Binding.KEY_V
                    22 -> Binding.KEY_W
                    23 -> Binding.KEY_X
                    24 -> Binding.KEY_Y
                    25 -> Binding.KEY_Z
                    else -> Binding.NONE
                }
            }
            Range.DIGITS -> {
                // 0-9: index 0-9 对应 Binding.KEY_0 到 Binding.KEY_9
                when (index) {
                    0 -> Binding.KEY_0
                    1 -> Binding.KEY_1
                    2 -> Binding.KEY_2
                    3 -> Binding.KEY_3
                    4 -> Binding.KEY_4
                    5 -> Binding.KEY_5
                    6 -> Binding.KEY_6
                    7 -> Binding.KEY_7
                    8 -> Binding.KEY_8
                    9 -> Binding.KEY_9
                    else -> Binding.NONE
                }
            }
            Range.FUNCTION_KEYS -> {
                // F1-F12: index 0-11 对应 Binding.KEY_F1 到 Binding.KEY_F12
                when (index) {
                    0 -> Binding.KEY_F1
                    1 -> Binding.KEY_F2
                    2 -> Binding.KEY_F3
                    3 -> Binding.KEY_F4
                    4 -> Binding.KEY_F5
                    5 -> Binding.KEY_F6
                    6 -> Binding.KEY_F7
                    7 -> Binding.KEY_F8
                    8 -> Binding.KEY_F9
                    9 -> Binding.KEY_F10
                    10 -> Binding.KEY_F11
                    11 -> Binding.KEY_F12
                    else -> Binding.NONE
                }
            }
            Range.NUMPAD_DIGITS -> {
                // NP0-NP9: index 0-9 对应 Binding.NUMPAD_0 到 Binding.NUMPAD_9
                when (index) {
                    0 -> Binding.NUMPAD_0
                    1 -> Binding.NUMPAD_1
                    2 -> Binding.NUMPAD_2
                    3 -> Binding.NUMPAD_3
                    4 -> Binding.NUMPAD_4
                    5 -> Binding.NUMPAD_5
                    6 -> Binding.NUMPAD_6
                    7 -> Binding.NUMPAD_7
                    8 -> Binding.NUMPAD_8
                    9 -> Binding.NUMPAD_9
                    else -> Binding.NONE
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
                val prevBinding = getBindingForRangeIndex(range, lastActivatedIndex)
                if (prevBinding != Binding.NONE) {
                    inputControlsView.handleInputEvent(prevBinding, false)
                }
            }

            // Activate new binding
            val binding = getBindingForRangeIndex(range, centerIndex)
            if (binding != Binding.NONE) {
                inputControlsView.handleInputEvent(binding, true)
                lastActivatedIndex = centerIndex
            }
        }
    }
}
