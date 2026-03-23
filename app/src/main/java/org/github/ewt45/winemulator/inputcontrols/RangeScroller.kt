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
            val binding = element.getBindingAt(lastActivatedIndex)
            if (binding != Binding.NONE) {
                inputControlsView.handleInputEvent(binding, false)
            }
            lastActivatedIndex = -1
        }
        isDragging = false
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
                val prevBinding = element.getBindingAt(lastActivatedIndex)
                if (prevBinding != Binding.NONE) {
                    inputControlsView.handleInputEvent(prevBinding, false)
                }
            }

            // Activate new binding
            val binding = element.getBindingAt(centerIndex)
            if (binding != Binding.NONE) {
                inputControlsView.handleInputEvent(binding, true)
                lastActivatedIndex = centerIndex
            }
        }
    }
}
