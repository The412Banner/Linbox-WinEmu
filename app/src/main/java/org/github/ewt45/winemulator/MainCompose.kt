package org.github.ewt45.winemulator

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch


@Composable
fun ProotOutputScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel(), ) {
    val TAG = "ProotOutputScreen"
    val output by viewModel.commandOutput.collectAsState()
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Proot 命令输出", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = output, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            scope.launch {
                val cmd = "cat /etc/apk/repositories"
                val output = Rootfs.runProotCommand(cmd)
                viewModel.setDebugInfo("执行命令 $cmd \n $output")
                Log.d(TAG, "ProotOutputScreen: 执行proot命令，输出 \n $output")
            }

        }) {
            Text("测试")
        }
    }
}

