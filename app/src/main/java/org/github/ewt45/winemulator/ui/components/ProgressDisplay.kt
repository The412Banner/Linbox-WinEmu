package org.github.ewt45.winemulator.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Returns a [SimpleTaskReporter] instance. */
@Composable
fun rememberTaskReporter(
    initStage: ProgressStage = ProgressStage.NOT_STARTED,
    progress: Int = 0,
    msgTitle: String = "",
    msg: String = ""
): SimpleTaskReporter {
    return remember { SimpleTaskReporter(initStage, progress, msgTitle, msg) }
}

/** Concrete [TaskReporter] backed by MutableState for direct use in Compose. Obtain via [rememberTaskReporter]. */
class SimpleTaskReporter(
    initStage: ProgressStage,
    initProgress: Int,
    initMsgTitle: String,
    initMsg: String,
) : TaskReporter() {
    var stage by mutableStateOf(initStage)
    var progress by mutableIntStateOf(initProgress)
    var msgTitle by mutableStateOf(initMsgTitle)
    var msg by mutableStateOf(initMsg)
    override fun progress(percent: Float) {
        progress = (percent * 100).toInt()
    }

    override fun done(error: Exception?) {
        progress = 100
        if (error != null) throw error
    }

    override fun msg(text: String?, title: String?) {
        if (!text.isNullOrBlank()) msg += "\n$text"
        if (title != null) msgTitle = title
    }

    fun component1() = this
    fun component2() = progress
    fun component3() = msgTitle
    fun component4() = msg
}

/**
 * Pass an instance of this to a long-running operation so it can report progress and messages to the UI.
 * @param totalValue denominator for percentage calculation; negative means progress is indeterminate
 */
abstract class TaskReporter(var totalValue: Long = -1) {

    /** Updates progress. A negative value means indeterminate (show an infinite progress bar). */
    abstract fun progress(percent: Float)

    /** Like [progress] but takes the raw current value instead of a fraction, for callers that don't know the total. */
    fun progressValue(value: Long) = progress(value.toFloat() / totalValue)

    /** Call to signal task completion. A non-null [error] indicates failure. */
    abstract fun done(error: Exception? = null)

    /** Sets the display text. When [title] is null, the last non-null title should remain visible. */
    abstract fun msg(text: String? = null, title: String? = null)

    companion object {
        val Dummy: TaskReporter = object : TaskReporter(Long.MAX_VALUE) {
            override fun progress(percent: Float) {}
            override fun done(error: Exception?) {}
            override fun msg(text: String?, title: String?) {}
        }
    }
}

enum class ProgressStage {
    NOT_STARTED, PROCESSING, DONE_SUCCESS, DONE_FAILURE
}

@Composable
fun ProgressDisplay(
    reporter: SimpleTaskReporter
) {
    ProgressDisplay(reporter.stage, reporter.progress, reporter.msgTitle, reporter.msg)
}

/**
 * @param progress 0-100
 */
@Composable
fun ProgressDisplay(
    stage: ProgressStage,
    progress: Int,
    msgTitle: String,
    msg: String,
) {
    val TAG = "ProgressDisplay"

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
//            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(msgTitle, style = MaterialTheme.typography.titleMedium)

            when (stage) {
                ProgressStage.NOT_STARTED -> Unit
                ProgressStage.PROCESSING ->
                    // Show progress bar while processing
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(progress = { progress / 100F })
                        Text("$progress%")
                    }

                ProgressStage.DONE_SUCCESS ->
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.secondary)

                ProgressStage.DONE_FAILURE ->
                    Icon(Icons.Rounded.Warning, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.error)
            }


            // Show log during and after processing; collapsed after successful extraction
            var msgExpanded by remember(stage) { mutableStateOf(stage == ProgressStage.PROCESSING) }
            if (stage != ProgressStage.NOT_STARTED) {
                // weight fills remaining space so buttons are not pushed off screen; scrollable when content is tall.
                // Both modifiers must go on the wrapping column, not on the Text itself.
                // Caveat: weight still fills all remaining space even when content is short, leaving a blank gap.
                Text(
                    msg,
                    Modifier
                        .fillMaxWidth()
                        .clickable { msgExpanded = !msgExpanded }
                        .animateContentSize()
                        .horizontalScroll(rememberScrollState()),
                    color = MaterialTheme.colorScheme.run { if (stage == ProgressStage.DONE_FAILURE) error else onSurface },
                    maxLines = if (msgExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}