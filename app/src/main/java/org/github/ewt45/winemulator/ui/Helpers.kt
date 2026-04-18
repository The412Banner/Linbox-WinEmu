package org.github.ewt45.winemulator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Attaches a click listener to an [interactionSource].
 * Useful for TextField where modifier.clickable does not fire.
 */
@Composable
fun InteractionSourceOnClick(interactionSource: MutableInteractionSource, onClick:  /*suspend CoroutineScope.*/() -> Unit) {
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { value ->
            if (value is PressInteraction.Release) {
                onClick()
            }
        }
    }
}

/**
 * Animated expand/collapse from the center of the composable.
 */
@Composable
fun AnimatedSizeInCenter(visible: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(
        visible,
        modifier,
        //animationSpec = tween(durationMillis = 300),
        enter = expandIn(expandFrom = Alignment.Center) + fadeIn(),
        exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
        content = content,
    )
}

@Composable
fun AnimatedVertical(
    visible: Boolean,
    modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = content,
    )

}

/**
 * Shows an alert dialog.
 * @param onDismiss called when the dialog is dismissed; set visible to false here
 * @param onConfirm called when the OK button is tapped
 * @param onCancel  called when the Cancel button is tapped
 * @param hideBtns  when true, hides buttons to prevent dismissal; [onDismiss] will not fire
 */
@Composable
fun MyDialog(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
    hideBtns: Boolean = false,
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = { if (!hideBtns) onDismiss() },
//                title = { Text("Loading") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            },
            confirmButton = { if (!hideBtns) TextButton(onClick = onConfirm) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { if (!hideBtns && onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } }
        )
    }

}

@Preview
@Composable
private fun Test() {
    MyDialog(
        visible = true,
        text = "111",
        onDismiss = {}
    )
}