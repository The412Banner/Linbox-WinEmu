package org.github.ewt45.winemulator.ui.setting

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.ui.components.ComposeSpinner
import org.github.ewt45.winemulator.ui.components.TitleAndContent
import org.github.ewt45.winemulator.ui.components.CollapsePanel
import org.github.ewt45.winemulator.ui.components.TextFieldOption
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * X11 resolution setting.
 * Supports preset resolutions and custom text input.
 * Uses general_resolution to stay consistent with the existing logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X11Resolution(
    text: String,
    onDone: (String, Boolean) -> Unit,
) {
    val options = listOf("800x600", "1024x768", "1280x720", "1600x900", "1920x1080")
    val textInOptions = options.contains(text)
    // isCustom is initialised based on whether the resolution is in the preset list; the user can toggle it manually
    var isCustom by remember { mutableStateOf(!textInOptions) }
    val realText = if (isCustom) "Custom" else text
    var expanded by remember { mutableStateOf(false) }

    TitleAndContent(
        title = "Resolution",
        subTitle = "Format: WxH (e.g. 1280x720). After editing tap the checkmark or press Enter to save."
    ) {
        ExposedDropdownMenuBox(
            modifier = Modifier.fillMaxWidth(),
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                value = realText,
                readOnly = true,
                onValueChange = {},
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
            )

            val contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Custom", style = MaterialTheme.typography.bodyLarge) },
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

        // Text field for manual input when Custom is selected
        AnimatedVisibility(visible = isCustom) {
            TextFieldOption(text = text, onDone = { onDone(it, true) })
        }
    }
}

/**
 * X11 settings panel.
 * Includes resolution, touch mode, screen orientation, display scale, and keep-screen-on options.
 */
@Composable
fun X11Settings(
    settingVm: SettingViewModel,
) {
    val x11State by settingVm.x11State.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollapsePanel("X11 Display Settings", vPadding = 32.dp) {
        // Resolution — uses the existing general_resolution preference
        X11Resolution(
            text = settingVm.resolutionText,
            onDone = settingVm::onChangeResolutionText
        )

        // Touch mode
        X11TouchMode(
            currentMode = x11State.touchMode,
            onModeChange = settingVm::onChangeX11TouchMode
        )

        // Screen orientation
        X11ScreenOrientation(
            currentOrientation = x11State.screenOrientation,
            onOrientationChange = settingVm::onChangeX11ScreenOrientation
        )

        // Display scale
        X11DisplayScale(
            scale = x11State.displayScale,
            onScaleChange = settingVm::onChangeX11DisplayScale
        )

        // Keep screen on
        X11KeepScreenOn(
            enabled = x11State.keepScreenOn,
            onEnabledChange = settingVm::onChangeX11KeepScreenOn
        )
    }
}

/**
 * Touch mode setting.
 * 0 = Virtual Touchpad, 1 = Simulated Touch (direct tap), 2 = Touchscreen mode
 */
@Composable
fun X11TouchMode(
    currentMode: Int,
    onModeChange: (Int) -> Unit,
) {
    val touchModeOptions = listOf(0, 1, 2)
    val touchModeNames = listOf("Virtual Touchpad", "Simulated Touch", "Touchscreen")
    val touchModeDescriptions = listOf(
        "Move cursor with one finger; tap to click",
        "Tap to move cursor; long-press for right-click",
        "Direct touchscreen input"
    )
    val currentModeName = touchModeNames.getOrElse(currentMode) { touchModeNames[0] }
    val currentDescription = touchModeDescriptions.getOrElse(currentMode) { touchModeDescriptions[0] }

    TitleAndContent(
        title = "Touch Mode",
        subTitle = "Choose how the touchscreen operates.\nCurrent: $currentModeName\n$currentDescription"
    ) {
        ComposeSpinner(
            currKey = currentMode,
            keyList = touchModeOptions,
            nameList = touchModeNames,
            modifier = Modifier.fillMaxWidth()
        ) { _, newValue ->
            onModeChange(newValue)
        }
    }
}

/**
 * Screen orientation setting.
 * Uses distinct values to avoid conflicts with system constants: 10=auto, 11=landscape, 12=portrait, 13=reverse landscape, 14=reverse portrait
 */
@Composable
fun X11ScreenOrientation(
    currentOrientation: Int,
    onOrientationChange: (Int) -> Unit,
) {
    val orientationOptions = listOf(10, 11, 12, 13, 14)
    val orientationNames = listOf("Follow System", "Landscape (fixed)", "Portrait (fixed)", "Reverse Landscape", "Reverse Portrait")

    TitleAndContent(
        title = "Screen Orientation",
        subTitle = "Controls the screen orientation for X11. Note: this is X11-specific and does not affect the system orientation."
    ) {
        ComposeSpinner(
            currKey = currentOrientation,
            keyList = orientationOptions,
            nameList = orientationNames,
            modifier = Modifier.fillMaxWidth()
        ) { _, newValue ->
            onOrientationChange(newValue)
        }
    }
}

/** Display scale setting. */
@Composable
fun X11DisplayScale(
    scale: Int,
    onScaleChange: (Int) -> Unit,
) {
    TitleAndContent(
        title = "Display Scale",
        subTitle = "Adjust the X11 display scale. Current: ${scale}%"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = scale.toFloat(),
                onValueChange = { onScaleChange(it.toInt()) },
                valueRange = 30f..300f,
                steps = 26, // (300-30)/10 - 1 = 26
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("30%", style = MaterialTheme.typography.bodySmall)
                Text("${scale}%", style = MaterialTheme.typography.bodyMedium)
                Text("300%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Keep screen on setting. */
@Composable
fun X11KeepScreenOn(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    TitleAndContent(
        title = "Keep screen on during runtime",
        subTitle = "When enabled, prevents the screen from sleeping while X11 is running."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Keep Screen On")
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

/** X11 settings preview. */
@Composable
fun X11SettingsPreview() {
    var resolution by remember { mutableStateOf("1280x720") }
    var touchMode by remember { mutableIntStateOf(0) }
    var screenOrientation by remember { mutableIntStateOf(10) }
    var displayScale by remember { mutableIntStateOf(100) }
    var keepScreenOn by remember { mutableStateOf(true) }

    CollapsePanel("X11 Settings Preview") {
        X11Resolution(resolution) { newValue, _ -> resolution = newValue }
        X11TouchMode(touchMode, { touchMode = it })
        X11ScreenOrientation(screenOrientation, { screenOrientation = it })
        X11DisplayScale(displayScale, { displayScale = it })
        X11KeepScreenOn(keepScreenOn, { keepScreenOn = it })
    }
}
