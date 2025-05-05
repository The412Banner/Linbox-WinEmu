package org.github.ewt45.winemulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.viewmodel.MainViewModel
import org.github.ewt45.winemulator.viewmodel.SettingAction
import org.github.ewt45.winemulator.viewmodel.SettingViewModel

@Composable
fun SettingScreen(modifier: Modifier = Modifier) {
    val TAG = "SettingScreen"
    val mainViewModel: MainViewModel = viewModel()
    val settingViewModel: SettingViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val proot by settingViewModel.prootState.collectAsState()
    //TODO 用LazyColumn?
    Column(modifier) {
        TopBarActions(modifier = Modifier.align(Alignment.End),)
        ProotSettings(
            prootNoValueOptions = proot.proot_bool_options,
            onChangeProotNoValueOptions = settingViewModel::onChangeProotBoolOptions,
            prootStartupCmd = proot.proot_startup_cmd,
            onChangeProotStartupCmd = settingViewModel::onChangeProotStartupCmd,
        )
    }
}

/**
 * 顶部操作按钮
 */
@Composable
fun TopBarActions(
    modifier: Modifier = Modifier,
) {
    val mainVM: MainViewModel = viewModel()
    val settingVM: SettingViewModel = viewModel()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    //导出时 保存为文件
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val th = settingVM.exportSettings(ctx, uri).exceptionOrNull()
            val resultStr = if (th != null) "导出失败。错误信息：\n\n${th.stackTraceToString()}" else "导出成功！"
            mainVM.showConfirmDialog(resultStr)
        }

    }
    //导入时 选择文件
    val readFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val th = settingVM.importSettings(ctx, uri).exceptionOrNull()
            th?.printStackTrace()
            val resultStr = if (th != null) "导入失败。错误信息：\n\n${th.stackTraceToString()}" else "导入成功！"
            mainVM.showConfirmDialog(resultStr)
        }
    }

    TopBarActions(
        modifier,
        onClick = { action ->
            scope.launch {
                when(action) {
                    SettingAction.EXPORT -> {
                        if (mainVM.showConfirmDialog("将设置导出为Json文件。请选择文件保存位置。").getOrNull() == true)
                            saveFileLauncher.launch("preferences.json")
                    }
                    SettingAction.IMPORT -> {
                        if (mainVM.showConfirmDialog("导入本地Json文件更新设置。请选择文件所在位置。").getOrNull() == true)
                            readFileLauncher.launch(arrayOf("text/*", "application/json"))
                    }
                    SettingAction.RESET -> {
                        if (mainVM.showConfirmDialog("将全部选项恢复为默认。是否执行此操作？").getOrNull() == true)
                            settingVM.resetSettings()
                    }
                }
            }
        }
    )
}

@Composable
fun TopBarActions(
    modifier: Modifier = Modifier,
    onClick: (SettingAction) -> Unit = {},
    ) {
    Row(modifier = modifier) {
        TextButton(onClick = { onClick(SettingAction.EXPORT) }) { Text("导出") }
        TextButton(onClick = { onClick(SettingAction.IMPORT) }) { Text("导入") }
        TextButton(onClick = { onClick(SettingAction.RESET) }) { Text("重置") }
    }
}


/**
 * proot设置
 */
@Composable
fun ProotSettings(
    prootNoValueOptions: Set<String>,
    onChangeProotNoValueOptions: (String, Boolean) -> Unit,
    prootStartupCmd: String,
    onChangeProotStartupCmd: (String) -> Unit,
) {
    CollapsePanel("PRoot参数") {
        ProotNoValueOptions(prootNoValueOptions, onChangeProotNoValueOptions)
        Spacer(Modifier.height(16.dp))
        ProotStartupCmd(prootStartupCmd, onChangeProotStartupCmd)
    }
}

/**
 * 一些无参数的选项
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProotNoValueOptions(
    options: Set<String>,
    onCheck: (String, Boolean) -> Unit
) {

    val optionRootId = options.contains("--root-id")
    val optionL = options.contains("-L")
    val optionLink2symlink = options.contains("--link2symlink")
    val optionKillOnExit = options.contains("--kill-on-exit")
    val optionSysvipc = options.contains("--sysvipc")
    val optionAshmemMemfd = options.contains("--ashmem-memfd")
    val optionH = options.contains("-H")
    val optionP = options.contains("-P")

    FlowRow(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipOption(onCheck, optionRootId, "-0,--root-id", "--root-id")
        ChipOption(onCheck, optionL, "-L")
        ChipOption(onCheck, optionLink2symlink, "-l,--link2symlink", "--link2symlink")
        ChipOption(onCheck, optionKillOnExit, "--kill-on-exit")
        ChipOption(onCheck, optionSysvipc, "--sysvipc")
        ChipOption(onCheck, optionAshmemMemfd, "--ashmem-memfd")
        ChipOption(onCheck, optionH, "-H")
        ChipOption(onCheck, optionP, "-P")
    }
}

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

@Composable
fun ProotStartupCmd(
    cmd: String,
    onChange: (String) -> Unit
) {
    //用户编辑内容时，先存到这里
    var tempValue by remember { mutableStateOf(cmd) }
    LaunchedEffect(cmd) { tempValue = cmd }
    //管理焦点，当编辑完成（点击回车/按钮）时退出焦点
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    TextField(
        label = { Text("启动后执行命令") },
        value = tempValue,
        onValueChange = { tempValue = it },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            AnimatedSizeInCenter(isFocused) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "完成",
                    modifier = Modifier.clickable {
                        onChange(tempValue)
                        focusManager.clearFocus()
                    })
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onChange(tempValue)
            focusManager.clearFocus()
        }),
        interactionSource = interactionSource
    )
}


@Composable
fun TextFieldPreference() {

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
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewExpandablePanelExample() {
//    SettingScreen()
    Column {
        TopBarActions(
            modifier = Modifier.align(Alignment.End),
            onClick = {}
        )
        ProotSettings(
            prootNoValueOptions = setOf(),
            onChangeProotNoValueOptions = { _, _ -> },
            prootStartupCmd = "",
            onChangeProotStartupCmd = {},
        )
    }
//    var finalCmd by remember { mutableStateOf("") }
//    ProotStartupCmd(finalCmd) {finalCmd = it.replace("\n", " ").trim().trimEnd('&').trim()}
}