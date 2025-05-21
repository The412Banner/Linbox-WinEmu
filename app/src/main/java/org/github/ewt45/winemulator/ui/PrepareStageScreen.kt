package org.github.ewt45.winemulator.ui


import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.FuncOnChangeAction
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.Utils
import org.github.ewt45.winemulator.emu.ProotRootfs
import org.github.ewt45.winemulator.ui.setting.GeneralRootfsSelect_LoginUserSelect
import org.github.ewt45.winemulator.ui.setting.GeneralRootfsSelect_RootfsName
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import java.io.File

@Composable
fun PrepareStageScreen() {
    val setting: SettingViewModel = viewModel()
    RootfsSelectScreen(setting::onChangeRootfsLoginUser, setting::onChangeRootfsName)
}


private enum class ProcessState {
    NOT_SELECTED, PROCESSING, DONE_SUCCESS, DONE_FAILURE
}

/**
 * @param onChangeUser 参考 [SettingViewModel.onChangeRootfsLoginUser]
 * @param onRootfsNameChange 参考 [SettingViewModel.onChangeRootfsName]
 */
@Composable
fun RootfsSelectScreen(
    onChangeUser: suspend (String, String) -> Unit,
    onRootfsNameChange: suspend (String, String, FuncOnChangeAction) -> String,
) {
    val TAG = "RootfsSelectScreen"
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var processState by remember { mutableStateOf(ProcessState.NOT_SELECTED) }
    var extractProgress by remember { mutableIntStateOf(0) } //0-100
    var processingMsgTitle by remember { mutableStateOf("缺少Rootfs。请点击按钮选择一个包含Rootfs的 .tar.xz 或 .tar.gz 压缩包。") }
    var processingMsg by remember { mutableStateOf("") }
    var rootfsName by remember { mutableStateOf("") }
    var isSetCurrent by remember { mutableStateOf(true) }

    val processReporter = object : Utils.TaskReporter(-1) {
        override fun progress(percent: Float) {
            extractProgress = (percent * 100).toInt()
        }

        override fun done(error: Exception?) {
            extractProgress = 100
            if (error != null) throw error
        }

        override fun msg(text: String?, title: String?) {
            if (!text.isNullOrBlank()) processingMsg += "\n$text"
            if (title != null) processingMsgTitle = title
        }
    }

    val readFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        processState = ProcessState.PROCESSING
        scope.launch {
            extractProgress = 0
            processingMsgTitle = "正在解压中，请等待完成。"
            processingMsg = "日志："
            try {
                rootfsName = Utils.Rootfs.installRootfsArchive(ctx, uri, processReporter).name
                processReporter.msg("解压rootfs成功。", "解压成功，点击按钮将退出。请手动重启。（日志可点击展开查看）")
                processState = ProcessState.DONE_SUCCESS
            } catch (e: Throwable) {
                e.printStackTrace()
                processReporter.msg(
                    "解压rootfs过程中出现错误，结束。\n" + e.stackTraceToString(),
                    "解压失败。请点击按钮选择一个包含Rootfs的 .tar.xz 或 .tar.gz 压缩包。\n（日志可点击展开查看）"
                )
                processState = ProcessState.DONE_FAILURE
            }
            extractProgress = 100
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题
            Text(processingMsgTitle)

            // 只有处理过程中显示进度条
            if (processState == ProcessState.PROCESSING) {
               Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(progress = { extractProgress / 100F })
                    Text("$extractProgress%")
                }
            }

            // 处理时或处理后显示日志。解压成功后折叠
            var msgExpanded by remember(processState) { mutableStateOf(processState == ProcessState.PROCESSING) }
            if (processState != ProcessState.NOT_SELECTED) {
                // weight 占据剩余空间，保证优先满足按钮的高度。否则会把按钮挤没。然后高度过高时可滚动。这俩modifier要加到包裹column上，加到text自身没用
                // 但是weight会在内容没那么高的时候还是占据所有剩余空间 导致空出来一大块。
                Text(
                    processingMsg,
                    Modifier
                        .fillMaxWidth()
                        .clickable { msgExpanded = !msgExpanded }
                        .horizontalScroll(rememberScrollState()),
                    color = MaterialTheme.colorScheme.run { if (processState == ProcessState.DONE_FAILURE) error else onSurface },
                    maxLines = if (msgExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // 需要解压时显示选择按钮
            if (processState == ProcessState.NOT_SELECTED || processState == ProcessState.DONE_FAILURE) {
                Button({ readFileLauncher.launch(arrayOf("application/x-xz", "*/*")) })
                { Text("选择") }
            }
            // 解压成功后显示完成按钮
            else if (processState == ProcessState.DONE_SUCCESS) {
                Button({
                    scope.launch {
                        if (isSetCurrent) MainEmuActivity.instance.settingViewModel.onChangeRootfsSelect(rootfsName)
                        else MainEmuActivity.instance.finish()
                    }
                }) { Text("完成") }
            }

            // 解压成功后后的其他选项，重命名，登陆用户，下次启动该容器。
            if (processState == ProcessState.DONE_SUCCESS && rootfsName.isNotEmpty()) {
                Log.e(TAG, "RootfsSelectScreen: 解压完成后进入这里检查可登陆用户列表。平时不会进入吧？")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("退出之前，您还可以编辑以下内容")

                GeneralRootfsSelect_RootfsName(rootfsName, false) { oldRootfsName, newRootfsName, _ ->
                    onRootfsNameChange(oldRootfsName, newRootfsName, FuncOnChangeAction.EDIT)
                }

                val userList = ProotRootfs.getUserInfos(File(Consts.rootfsAllDir, rootfsName)).map { it.name }
                userList.find { it != "root" }?.let { nonRootUser ->
                    var userName by remember { mutableStateOf(nonRootUser) }
                    GeneralRootfsSelect_LoginUserSelect(rootfsName, userName, userList) { rootfsName, newUserName ->
                        userName = newUserName
                        scope.launch { onChangeUser(rootfsName, newUserName) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("下次启动app运行该容器")
                    Checkbox(isSetCurrent, { isSetCurrent = it })
                }
            }
        }
    }
}

//@Preview(widthDp = 300, heightDp = 600)
@Composable
fun PrepareStageScreenFinishPreview() {
    Column(
        Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val rootfsName = "rootfs-1"
        Text("退出之前，您还可以编辑以下内容。。")
        Spacer(Modifier.height(16.dp))
        GeneralRootfsSelect_RootfsName("rootfs-1", false) { _, _, _ -> "" }

        val userList = listOf("root", "aid_u0_a287", "iuser").filter { !it.startsWith("aid_") }.sorted()
        val nonRootUser = userList.find { it != "root" }
        if (nonRootUser != null) {
            var userName by remember { mutableStateOf(nonRootUser) }
            Spacer(Modifier.height(16.dp))
            GeneralRootfsSelect_LoginUserSelect(rootfsName, userName, userList) { _, newUserName -> userName = newUserName }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下次启动app运行该容器")
            Checkbox(true, {})
        }
    }
}

@Preview(widthDp = 300, heightDp = 600)
@Composable
fun PrepareStageScreenPreview() {
    RootfsSelectScreen({ _, _ -> }, { _, _, _ -> "" })

    Spacer(Modifier.height(32.dp))
}