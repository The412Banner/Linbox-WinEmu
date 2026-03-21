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
    private val rangeIndex = byteArrayOf(0, 0)
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging = false

    fun getElementSize(): Float {
        return inputControlsView.snappingSize * 4f
    }

    fun getScrollOffset(): Float = scrollOffset

    fun getRangeIndex(): ByteArray = rangeIndex

    fun handleTouchDown(element: ControlElement, x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
        isDragging = true
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

        val index = (position.toInt()).coerceIn(0, range.max.toInt() - 1)
        rangeIndex[0] = index.toByte()
        rangeIndex[1] = (index + 1).toByte().coerceAtMost(range.max)

        // Trigger the binding for the current index
        val binding = element.getBindingAt(index)
        inputControlsView.handleInputEvent(binding, true)
    }
}
