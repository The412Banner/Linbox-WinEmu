package org.github.ewt45.winemulator.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.github.ewt45.winemulator.ui.AnimatedSizeInCenter
import org.github.ewt45.winemulator.ui.AnimatedVertical

@Composable
fun ChipOption(
    onCheck: (String, Boolean) -> Unit,
    state: Boolean,
    label: String,
    key: String = label
) {
    FilterChip(
        state,
        onClick = { onCheck(key, !state) },
        label = { Text(label) },
    )
}

/**
 * TextField.
 * 输入（获取焦点）时，右侧显示对号图标，
 * 点击图标或输入法回车时失去焦点并执行onDone回调
 */
@Composable
fun TextFieldOption(
    modifier: Modifier = Modifier,
    title: String? = null,
    text: String,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onDone: (String) -> Unit
) {
    //用户编辑内容时，先存到这里
    var tempValue by remember { mutableStateOf(text) }
    LaunchedEffect(text) { tempValue = text }
    //管理焦点，当编辑完成（点击回车/按钮）时退出焦点
    val focusManager = LocalFocusManager.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    val onDoneClick:()->Unit = {
        onDone(tempValue)
        focusManager.clearFocus()
    }
    TextField(
        label = title?.let { { Text(it) } },
        value = tempValue,
        onValueChange = { tempValue = it },
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            AnimatedSizeInCenter(isFocused) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "完成",
                    modifier = Modifier.clickable(onClick = onDoneClick)
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDoneClick() }),
        interactionSource = interactionSource
    )
}

/**
 * 折叠面板
 */
@Composable
fun CollapsePanel(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开"
            )
        }
        // 展开内容
        AnimatedVertical(expanded,) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}