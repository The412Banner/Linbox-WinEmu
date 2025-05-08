package org.github.ewt45.winemulator.ui.setting

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import org.github.ewt45.winemulator.ui.AnimatedSizeInCenter
import org.github.ewt45.winemulator.ui.AnimatedVertical
import org.github.ewt45.winemulator.ui.InteractionSourceOnClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralResolution(
    text:String,
    onDone: (String, Boolean) -> Unit,
) {
    val options = listOf("800x600", "1024x768", "1280x720", "1600x900", "1920x1080")
    val textInOptions  = options.contains(text)
    // isCustom初始根据 分辨率是否在给定列表中 设定。后续可以手动修改用于表示用户点击了 该选项
    var isCustom by remember { mutableStateOf(!textInOptions) }
    val realText = if (isCustom) "自定义" else text
    var expanded by remember { mutableStateOf(false) }

    // 用户点击菜单项“自定义” -> 回调中设置isCustom为true -> 这种情况下不调用onDone？
    // TextField显示文字在isCustom时为 “自定义” 否则为传进来的分辨率。
    // TextField onValueChange啥也不做吧，通知viewmodel都放到点击选项时的回调里
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth(),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
//                .focusRequester(focusRequester)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            value = realText,
            readOnly = true,
            onValueChange = {},
            singleLine = true,
            label = { Text("分辨率（宽x高）") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )

        val contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            //TODO 添加宽高比选择
//            Row(modifier = Modifier
//                .padding(contentPadding)
//                .horizontalScroll(ScrollState(0))
//            ) {
//                TextButton(onClick = {}) { Text("4:3") }
//                TextButton(onClick = {}) { Text("16:9") }
//                TextButton(onClick = {}) { Text("9:16") }
//            }
            DropdownMenuItem(
                text = { Text("自定义", style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    expanded = false
                    isCustom = true
                },
                contentPadding = contentPadding,
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onDone(option, false)
                        expanded = false
                        isCustom = false
                    },
                    contentPadding = contentPadding,
                )
            }
        }
    }

    //自定义时手动输入的文本框
    AnimatedVertical(isCustom) {
        TextFieldOption(text = text, onDone = { onDone(it, true) })
    }

}

@Deprecated("")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralResolution2(
    text: String,
    onChange: (String) -> Unit,
    format: (String) -> String? ,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("800x600", "1024x768", "1280x720", "1600x900", "1920x1080")

    //使用exposed组件 焦点有问题，没法用对号图标。只能每修改一个字母 提交一次更改

    //放弃了。还是在自定义的时候额外显示一个编辑框吧, 手动完成来触发回调。即时更改会导致x11 activity清除焦点，每写一个字就要点一次。。。

    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth(),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
//                .focusRequester(focusRequester)
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            value = text,
            onValueChange = onChange,
            singleLine = true,
            label = { Text("分辨率（宽x高）") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            keyboardActions = KeyboardActions(onDone = {
                //强制设置为符合格式的分辨率
                onChange(format(text) ?: options[0])
                expanded = false
            }),
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }

//    ExposedDropdownMenuBox(
//        modifier = Modifier.fillMaxWidth(),
//        expanded = expanded,
//        onExpandedChange = { if(!editing) expanded = it }, //自定义且正在编辑时，不显示菜单
//    ) {
//        TextField(
//            // The `menuAnchor` modifier must be passed to the text field to handle
//            // expanding/collapsing the menu on click. A read-only text field has
//            // the anchor type `PrimaryNotEditable`.
//            modifier = Modifier
//                .fillMaxWidth()
//                .focusRequester(focusRequester)
//                .then(Modifier.menuAnchor(if (!custom) MenuAnchorType.PrimaryNotEditable else MenuAnchorType.PrimaryEditable)),
//            value = selectedItem,
//            onValueChange = { selectedItem = it },
//            readOnly = !custom,
//            singleLine = true,
//            label = { Text("分辨率（宽x高）"/* style = MaterialTheme.typography.titleMedium*/) },
//            trailingIcon = {
//                if (editing)
//                    Icon(
//                        Icons.Filled.Check,null,
//                        modifier = Modifier.clickable {
//                            //TODO 保存
////                            onChange(tempValue)
//                            focusManager.clearFocus()
//                        }
//                    )
//                else
//                    ExposedDropdownMenuDefaults.TrailingIcon(
//                    expanded = expanded,
//                    modifier = Modifier //FIXME ??为啥取反就不行？？？
//                )
//            },
//            //TODO 限制输入类型 / 输入错误提示
//            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Decimal),
//            keyboardActions = KeyboardActions(onDone = {
//                //TODO 保存 onChange(tempValue)
//                focusManager.clearFocus()
//            }),
//            colors = ExposedDropdownMenuDefaults.textFieldColors(),
//            interactionSource = interactionSource,
//        )
//        ExposedDropdownMenu(
//            expanded = expanded,
//            onDismissRequest = { expanded = false },
//        ) {
//            DropdownMenuItem(
//                text = { Text("自定义", style = MaterialTheme.typography.bodyLarge) },
//                onClick = {
//                    selectedItem = "800x600"
//                    expanded = false
//                    custom = true
//                },
//                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
//            )
//            options.forEach { option ->
//                DropdownMenuItem(
//                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
//                    onClick = {
//                        selectedItem = option
//                        expanded = false
//                        custom = false
//                    },
//                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
//                )
//            }
//        }
//    }
}

@Deprecated("")
// 第一种方案。自己实现的简陋下拉菜单，宽度和textfield不对齐
@Composable
fun GeneralResolution1() {
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf("选择一个选项") }
    val options = listOf("800x600", "1024x768", "1280x720", "1600x900", "1920x1080")
    val customState = remember { mutableStateOf(false) } //TODO 初始值视情况而定（如果当前值在列表里没有则为自定义）
    var custom by customState

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()

    val editing = custom && isFocused

    SideEffect {
        Log.d(
            "TAG",
            "GeneralResolution: 发生重组。expand: $expanded, isFocused: $isFocused, custom: $custom, editing: $editing, selectItem: $selectedItem"
        )
    }


//
    InteractionSourceOnClick(interactionSource) { expanded = !expanded }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextFieldOption(
            modifier = Modifier.focusRequester(focusRequester),
            text = selectedItem,
            interactionSource = interactionSource,
        ) { }
        DropdownMenu(
            modifier = Modifier.focusable(false),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        selectedItem = option
                        expanded = false
                    },
                )
            }
        }
        AnimatedSizeInCenter(
            !isFocused, Modifier
                .align(Alignment.CenterEnd)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Filled.Edit, null,
                modifier = Modifier.clickable { focusRequester.requestFocus() }

            )
        }
        if (!isFocused) {

        }

    }
}