package org.github.ewt45.winemulator.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
//        Spacer(Modifier.height(16.dp))
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
fun ProotStartupCmd(
    cmd: String,
    onChange: (String) -> Unit
) {
    TextFieldOption(title = "启动后执行命令", text = cmd, onDone = onChange)
}
