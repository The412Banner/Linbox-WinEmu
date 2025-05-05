package org.github.ewt45.winemulator.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun TestUi() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = { TextButton(onClick = {}){ Text("确定") } },
        text = {Text("文本文本文本",style = MaterialTheme.typography.bodyMedium)}
    )
}