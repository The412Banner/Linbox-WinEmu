package org.github.ewt45.winemulator.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.github.ewt45.winemulator.ui.AnimatedSizeInCenter
import org.github.ewt45.winemulator.ui.AnimatedVertical


/** Returns a state that controls showing a "not yet implemented" dialog. */
@Composable
fun rememberNotImplDialog(): MutableState<Boolean> {
    return rememberSimpleDialog("Not yet implemented!")
}

/**
 * Returns a state that controls the visibility of a [SimpleDialog].
 * @param onDismiss called when the dialog is dismissed; no need to handle visibility here
 */
@Composable
fun rememberSimpleDialog(text: String, title: String? = null, onDismiss: (() -> Unit)? = null): MutableState<Boolean> {
    val visibleState = remember { mutableStateOf(false) }
    SimpleDialog(visibleState.value, text, title) {
        visibleState.value = false
        if (onDismiss != null) onDismiss()
    }
    return visibleState
}

/** Simple alert dialog. */
@Composable
fun SimpleDialog(visible: Boolean, text: String, title: String? = null, onDismiss: (Boolean) -> Unit) {
    if (visible) {
        AlertDialog(
            onDismissRequest = {}, // prevent dismissal on outside tap
            title = title?.let { { Text(it) } },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDismiss(false) }) { Text(stringResource(android.R.string.ok)) }
            },
//        dismissButton = {
//            TextButton(onClick = { viewModel.closeConfirmDialog(false) }) { Text(stringResource(android.R.string.cancel)) }
//        }
        )
    }

}


/**
 * An ExposedDropdownMenuBox that shows a TextField; tapping opens a dropdown to switch options.
 * @param T key data type
 * @param currKey the currently selected key
 * @param keyList all selectable keys
 * @param nameList display names — must be in the same order as [keyList]; defaults to key.toString()
 * @param onSelectedChange callback when the user picks a different option; param 1 = oldValue, param 2 = newValue
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ComposeSpinner(
    currKey: T,
    keyList: List<T>,
    nameList: List<String> = keyList.map { it.toString() },
    modifier: Modifier = Modifier,
    label: String? = null,
    onSelectedChange: (T, T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var currIdx = keyList.indexOf(currKey)
    if (currIdx == -1) {
        Log.e("TAG", "ComposeSpinner: current selection not in list! $currKey, $keyList")
        currIdx = 0
    }
    ExposedDropdownMenuBox(
        expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            modifier = modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            value = nameList[currIdx], onValueChange = {}, readOnly = true, singleLine = true,
            label = label.takeIf { !label.isNullOrBlank() }?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = !expanded }) {

            keyList.forEachIndexed { idx, option ->
                DropdownMenuItem(
                    { Text(nameList[idx], style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        expanded = false
                        onSelectedChange(currKey, option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun TitleAndContent(title: String, subTitle: String = "", modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        var infoExpanded by remember { mutableStateOf(false) }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subTitle.isNotEmpty()) {
            Text(
                subTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (infoExpanded) Int.MAX_VALUE else 3,
                overflow = if (infoExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier.clickable { infoExpanded = !infoExpanded },
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * @param state whether the chip is currently selected
 * @param key a key passed to [onCheck] when clicked
 * @param label display text; defaults to [key]
 * @param onCheck callback on chip click; param 1 = [key], param 2 = new selected state (true = selected)
 */
@Composable
fun ChipOption(
    state: Boolean,
    key: String,
    label: String = key,
    enabled: Boolean = true,
    onCheck: (String, Boolean) -> Unit,
) {
    FilterChip(
        state,
        enabled = enabled,
        onClick = { onCheck(key, !state) },
        label = { Text(label) },
    )
}

/**
 * TextField that shows a checkmark icon on the right while focused.
 * Tapping the icon or pressing the IME Done action clears focus and fires [onDone].
 * @param onDone called with the new text when the user confirms a change; not called if the text is unchanged
 */
@Composable
fun TextFieldOption(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    outlined: Boolean = false,
    supportingText: @Composable() (() -> Unit)? = null,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onDone: (String) -> Unit
) {
    // Holds the in-progress edit before confirmation
    var tempValue by remember(text) { mutableStateOf(text) }
//    LaunchedEffect(text) { tempValue = text }
    LaunchedEffect(text) { Log.d("TAG", "TextFieldOption: text changed: text=$text, tempValue=$tempValue") }
    // Clear focus when editing is confirmed
    val focusManager = LocalFocusManager.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    val onDoneClick: () -> Unit = {
        if (tempValue != text)
            onDone(tempValue)
        focusManager.clearFocus()
    }
    val _label: @Composable() (() -> Unit)? = title?.let { { Text(it) } }
    val _value = tempValue
    val _onValueChange: (String) -> Unit = { tempValue = it }
    val _modifier = modifier.fillMaxWidth()
    val _trailingIcon: @Composable() (() -> Unit) = {
        AnimatedSizeInCenter(isFocused) {
            IconButton(onDoneClick, Modifier.size(32.dp)) { Icon(Icons.Filled.Check, contentDescription = "Done") }
        }
    }
    val _keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    val _keyboardActions = KeyboardActions(onDone = { onDoneClick() })

    if (outlined) {
        OutlinedTextField(
            label = _label,
            value = _value,
            onValueChange = _onValueChange,
            modifier = _modifier,
            trailingIcon = _trailingIcon,
            keyboardOptions = _keyboardOptions,
            keyboardActions = _keyboardActions,
            interactionSource = interactionSource,
            supportingText = supportingText,
            enabled = enabled,
            maxLines = maxLines,
        )
    } else {
        TextField(
            label = _label,
            value = _value,
            onValueChange = _onValueChange,
            modifier = _modifier,
            trailingIcon = _trailingIcon,
            keyboardOptions = _keyboardOptions,
            keyboardActions = _keyboardActions,
            interactionSource = interactionSource,
            supportingText = supportingText,
            enabled = enabled,
            maxLines = maxLines,
        )
    }
}

/** Collapsible panel. */
@Composable
fun CollapsePanel(
    title: String,
    initExpanded: Boolean = true,
    vPadding: Dp = 16.dp,
    content: @Composable() (ColumnScope.() -> Unit),
) {
    var expanded by remember { mutableStateOf(initExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        // Expandable content
        AnimatedVertical(expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(vPadding),
            ) {
                content()
            }
        }
    }
}

@Preview(widthDp = 300, heightDp = 600)
@Composable
fun TitleAndContentPreview() {
    CollapsePanel("Collapse") {
        TitleAndContent("Title", "Description") {
            Text("Content")
            Button({}) { Text("Button") }
        }
    }
}