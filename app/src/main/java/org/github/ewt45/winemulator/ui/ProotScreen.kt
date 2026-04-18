package org.github.ewt45.winemulator.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.github.ewt45.winemulator.ui.theme.*
import org.github.ewt45.winemulator.viewmodel.TerminalViewModel

/**
 * PRoot terminal screen — minimal full-screen terminal.
 * Dark theme, integrated input field, system keyboard.
 */
@Composable
fun ProotTerminalScreen(viewModel: TerminalViewModel) {
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        // Top status bar — minimal design
        TerminalHeaderBar(
            currentUser = viewModel.currentUser,
            currentHost = viewModel.currentHost,
            currentPath = viewModel.currentPath,
            isConnected = viewModel.isConnected
        )
        
        // Terminal output area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Focus the input field when the terminal area is tapped
                    focusRequester.requestFocus()
                }
        ) {
            val textVScroll = rememberScrollState()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(textVScroll)
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    ColoredTerminalOutput(
                        output = viewModel.output.value,
                        coloredPrompt = viewModel.getColoredPrompt()
                    )
                }
            }
            
            // Auto-scroll to bottom on new output
            LaunchedEffect(viewModel.output.value) {
                textVScroll.animateScrollTo(textVScroll.maxValue)
            }
        }
        
        // Built-in command input row — integrated at the bottom of the terminal
        IntegratedCommandInput(
            value = inputValue,
            onValueChange = { inputValue = it },
            onSendCommand = {
                if (inputValue.text.isNotBlank()) {
                    viewModel.runCommand(inputValue.text)
                    inputValue = TextFieldValue("")
                }
            },
            focusRequester = focusRequester,
            onFocusChanged = { isInputFocused = it }
        )
    }
}

/** Terminal top status bar — minimal style. */
@Composable
fun TerminalHeaderBar(
    currentUser: String,
    currentHost: String,
    currentPath: String,
    isConnected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        ConnectionIndicator(isConnected = isConnected)

        Spacer(modifier = Modifier.width(10.dp))

        // Prompt
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = TerminalUserGreen, fontWeight = FontWeight.Medium)) {
                    append(currentUser)
                }
                withStyle(SpanStyle(color = TerminalSymbolYellow)) {
                    append("@")
                }
                withStyle(SpanStyle(color = TerminalHostCyan, fontWeight = FontWeight.Medium)) {
                    append(currentHost)
                }
                withStyle(SpanStyle(color = TerminalSymbolYellow)) {
                    append(":")
                }
                withStyle(SpanStyle(color = TerminalPathWhite)) {
                    append(currentPath)
                }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

/** Connection status indicator — small dot. */
@Composable
fun ConnectionIndicator(isConnected: Boolean) {
    val statusColor by animateColorAsState(
        targetValue = if (isConnected) TerminalOnlineGreen else TerminalOfflineRed,
        animationSpec = tween(300),
        label = "statusColor"
    )
    
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(statusColor)
    )
}

/**
 * Built-in command input row — integrated at the terminal bottom.
 * Tapping opens the system keyboard.
 */
@Composable
fun IntegratedCommandInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSendCommand: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prompt prefix
        Text(
            text = "> ",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = TerminalSymbolYellow,
            fontWeight = FontWeight.Bold
        )
        
        // Editable input area
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    onFocusChanged(state.isFocused)
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = TerminalOnSurface,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(TerminalCursor),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                keyboardType = KeyboardType.Ascii
            ),
            keyboardActions = KeyboardActions(
                onSend = { onSendCommand() }
            ),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "Click to enter command...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TerminalOnSurface.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        // Send button (shown only when there is input)
        if (value.text.isNotEmpty()) {
            TextButton(
                onClick = onSendCommand,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Send",
                    color = TabIndicatorPurple,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Colored terminal output renderer.
 * Detects prompt lines and renders them with per-segment colors.
 */
@Composable
fun ColoredTerminalOutput(
    output: List<String>,
    coloredPrompt: AnnotatedString
) {
    // Regex to detect prompt lines
    val promptRegex = Regex("""([\w]+)@([\w.]+):([/~][^\s$#]*)([#$])(\s*)?$""")
    
    val annotatedOutput = buildAnnotatedString {
        output.forEach { line ->
            var remaining = line
            while (remaining.isNotEmpty()) {
                val matchResult = promptRegex.find(remaining)
                
                if (matchResult != null && matchResult.range.first >= 0) {
                    val beforePrompt = remaining.substring(0, matchResult.range.first)
                    val userName = matchResult.groupValues[1]
                    val hostName = matchResult.groupValues[2]
                    val path = matchResult.groupValues[3]
                    val symbol = matchResult.groupValues[4]
                    val trailing = matchResult.groupValues[5]
                    
                    if (beforePrompt.isNotEmpty()) {
                        append(beforePrompt)
                    }
                    
                    // Username color
                    val userColor = if (userName == "root") TerminalRootWhite else TerminalUserGreen
                    
                    withStyle(SpanStyle(color = userColor, fontWeight = FontWeight.Medium)) {
                        append(userName)
                    }
                    withStyle(SpanStyle(color = TerminalSymbolYellow)) {
                        append("@")
                    }
                    withStyle(SpanStyle(color = TerminalHostCyan, fontWeight = FontWeight.Medium)) {
                        append(hostName)
                    }
                    withStyle(SpanStyle(color = TerminalSymbolYellow)) {
                        append(":")
                    }
                    withStyle(SpanStyle(color = TerminalPathWhite)) {
                        append(path)
                    }
                    withStyle(SpanStyle(color = TerminalSymbolYellow)) {
                        append(symbol)
                        if (trailing.isNotEmpty()) {
                            append(trailing)
                        }
                    }
                    
                    val afterPromptStart = matchResult.range.last + 1
                    if (afterPromptStart < remaining.length) {
                        remaining = remaining.substring(afterPromptStart)
                    } else {
                        remaining = ""
                    }
                } else {
                    append(remaining)
                    remaining = ""
                }
            }
            append("\n")
        }
    }
    
    Text(
        text = annotatedOutput,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 18.sp
        ),
        color = TerminalOnSurface,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Terminal preview composable. */
@Composable
fun ProotTerminalScreenPreview() {
    val output = remember { mutableStateListOf(
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
        "  Linux Terminal Ready",
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
        "",
        "root@localhost:~$ ls -la",
        "total 64",
        "drwxr-xr-x  5 root root 4096 Apr  7 08:00 .",
        "drwxr-xr-x  3 root root 4096 Apr  7 08:00 ..",
        "-rw-r--r--  1 root root 4096 Apr  7 08:00 file1.txt",
        "root@localhost:~$ "
    ) }
    
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
    ) {
        TerminalHeaderBar(
            currentUser = "root",
            currentHost = "localhost",
            currentPath = "~",
            isConnected = true
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val textVScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(textVScroll)
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    ColoredTerminalOutput(
                        output = output.toList(),
                        coloredPrompt = buildAnnotatedString { }
                    )
                }
            }
        }
        
        IntegratedCommandInput(
            value = inputValue,
            onValueChange = { inputValue = it },
            onSendCommand = {
                if (inputValue.text.isNotBlank()) {
                    output.add("root@localhost:~$ ${inputValue.text}")
                    output.add("Command executed: ${inputValue.text}")
                    inputValue = TextFieldValue("")
                }
            },
            focusRequester = focusRequester,
            onFocusChanged = { }
        )
    }
}
