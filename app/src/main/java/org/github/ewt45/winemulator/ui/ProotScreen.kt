package org.github.ewt45.winemulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.viewmodel.TerminalViewModel

/**
 * Proot终端界面 - 集成美化后的终端UI
 * 支持显示用户名@主机:路径 格式的状态栏
 */
@Composable
fun ProotTerminalScreen(viewModel: TerminalViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentHost by viewModel.currentHost.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val isConnected by remember { viewModel.isConnected }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部状态栏 - 显示用户名、主机名和当前目录
        TerminalStatusBar(
            currentUser = currentUser,
            currentHost = currentHost,
            currentPath = currentPath,
            isConnected = isConnected
        )
        
        // 终端输出和输入区域
        ProotTerminalContent(
            output = viewModel.output.value,
            onRunCommand = { viewModel.runCommand(it) }
        )
    }
}

/**
 * 终端内容区域 - 输出和输入
 */
@Composable
fun ProotTerminalContent(
    output: List<String>,
    onRunCommand: (String) -> Unit
) {
    val TAG = "ProotOutputScreen"
    var execCommand by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val textVScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(textVScroll)
                //emmm加这个横向滚动 导致文本很短时，Text无法占满宽度了. 好了，在外层套一个Column就行了。
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = output.joinToString(separator = ""),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }


        //有内容更新时自动滚动到最底部
        LaunchedEffect(output.size) {
            textVScroll.animateScrollTo(textVScroll.maxValue)
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // 命令输入区域美化
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(4.dp)
        ) {
            TextField(
                value = execCommand,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { execCommand = it },
                label = { 
                    Text(
                        text = "输入命令",
                        fontFamily = FontFamily.Monospace
                    ) 
                },
                placeholder = {
                    Text(
                        text = "请输入命令...",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (execCommand.isNotBlank()) {
                                onRunCommand(execCommand)
                                execCommand = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (execCommand.isNotBlank()) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (execCommand.isNotBlank()) {
                            onRunCommand(execCommand)
                            execCommand = ""
                        }
                    }
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 兼容旧版本的函数（保留用于预览等场景）
 */
@Composable
fun ProotTerminalScreenImpl(
    output: SnapshotStateList<String>,
    runCommand: (String) -> Unit,
) {
    ProotTerminalContent(
        output = output.toList(),
        onRunCommand = runCommand
    )
}

@Preview
@Composable
fun ProotTerminalScreenPreview() {
    val output = remember { mutableStateListOf<String>() }
    ProotTerminalScreenImpl(output) { output.add(it + '\n') }
}

/**
 * 终端状态栏组件
 * 显示用户名@主机:路径 格式
 */
@Composable
fun TerminalStatusBar(
    currentUser: String,
    currentHost: String,
    currentPath: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isConnected) {
        MaterialTheme.colorScheme.primary // 主题主色 - 已连接
    } else {
        MaterialTheme.colorScheme.tertiary // 警告色 - 连接中
    }
    
    val userColor = if (currentUser == "root") {
        MaterialTheme.colorScheme.error // 红色 - root用户
    } else {
        MaterialTheme.colorScheme.secondary // 蓝色 - 普通用户
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            // 主提示符行
            Text(
                text = "${currentUser}@${currentHost}:${currentPath}$",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // 连接状态指示
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (isConnected) "● 终端已连接" else "○ 连接中...",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                color = statusColor,
                maxLines = 1
            )
        }
    }
}
