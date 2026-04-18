package org.github.ewt45.winemulator.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.FuncOnChangeAction
import org.github.ewt45.winemulator.inputcontrols.ControlsProfile
import org.github.ewt45.winemulator.inputcontrols.InputControlsManager
import org.github.ewt45.winemulator.ui.components.*
import org.github.ewt45.winemulator.ui.setting.*
import org.github.ewt45.winemulator.viewmodel.SettingViewModel

/**
 * Floating settings popup type enum
 */
enum class FloatingPopupType {
    NONE,
    GENERAL_SETTINGS,
    VIRTUAL_KEYS_SETTINGS,
    X11_SETTINGS
}

/**
 * Floating popup state management
 */
class FloatingPopupState {
    var currentPopup by mutableStateOf(FloatingPopupType.NONE)
        private set

    fun showPopup(type: FloatingPopupType) {
        currentPopup = type
    }

    fun dismissPopup() {
        currentPopup = FloatingPopupType.NONE
    }
}

/**
 * Composable floating popup container
 * Shows the appropriate settings popup based on current state
 */
@Composable
fun FloatingSettingsPopups(
    popupState: FloatingPopupState,
    settingVm: SettingViewModel,
    onVirtualKeysSettingsChanged: () -> Unit = {},  // Added: virtual keys settings change callback for immediate refresh
    modifier: Modifier = Modifier
) {
    when (popupState.currentPopup) {
        FloatingPopupType.NONE -> { /* Show no popup */ }
        FloatingPopupType.GENERAL_SETTINGS -> {
            GeneralSettingsPopup(
                settingVm = settingVm,
                onDismiss = { popupState.dismissPopup() }
            )
        }
        FloatingPopupType.VIRTUAL_KEYS_SETTINGS -> {
            VirtualKeysSettingsPopup(
                onDismiss = { popupState.dismissPopup() },
                onSettingsChanged = onVirtualKeysSettingsChanged  // Pass callback
            )
        }
        FloatingPopupType.X11_SETTINGS -> {
            X11SettingsPopup(
                settingVm = settingVm,
                onDismiss = { popupState.dismissPopup() }
            )
        }
    }
}

/**
 * General settings floating popup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsPopup(
    settingVm: SettingViewModel,
    onDismiss: () -> Unit
) {
    val generalState by settingVm.generalState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                // Title bar
                TopAppBar(
                    title = { Text("General Settings") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                // Refresh config
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )

                // Settings content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Container language setting
                    GeneralRootfsLang(
                        currLang = generalState.rootfsLang,
                        langOptions = listOf("en_US.utf8", "zh_CN.utf8"),
                        onLangChange = settingVm::onChangeRootfsLang
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Shared folder setting
                    GeneralShareDir(
                        bindSet = generalState.sharedExtPath,
                        onPathChange = settingVm::onChangeShareExtPath
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rootfs switch
                    GeneralRootfsSelect(
                        currRootfs = Consts.rootfsCurrDir.canonicalFile.name,
                        rootfsToLoginUserMap = generalState.localRootfsLoginUsersMap,
                        loginUsersOptions = settingVm.rootfsUsersOptions.value.mapValues { it.value.map { info -> info.name } },
                        rootfsAliasMap = settingVm.rootfsAliasMap.value,
                        onRootfsNameChange = settingVm::onChangeRootfsName,
                        onRootfsSelectChange = settingVm::onChangeRootfsSelect,
                        onUserSelectChange = { rootfs, user -> scope.launch { settingVm.onChangeRootfsLoginUser(rootfs, user) } },
                        onAliasChange = settingVm::onChangeRootfsAlias,
                        navigateToNewRootfs = { /* Navigate to new rootfs */ }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Virtual keys settings floating popup
 * @param onSettingsChanged Settings change callback to notify the caller to immediately refresh the virtual keys view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualKeysSettingsPopup(
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit = {}  // Added callback parameter
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val dialogState = rememberConfirmDialogState()

    var profiles by remember { mutableStateOf<List<ControlsProfile>>(emptyList()) }
    var selectedProfile by remember { mutableStateOf<ControlsProfile?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var isControlsEnabled by remember { mutableStateOf(false) }

    val manager = remember { InputControlsManager(context) }

    // Load profiles
    LaunchedEffect(Unit) {
        manager.loadProfiles(ignoreTemplates = false)
        profiles = manager.getProfiles()
        val savedId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)
        selectedProfile = if (savedId != 0) manager.getProfile(savedId) else null
        if (selectedProfile != null && savedId != selectedProfile!!.id) {
            prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, selectedProfile!!.id).apply()
        }
        showControls = prefs.getBoolean("show_touchscreen_controls", false)
        isControlsEnabled = savedId != 0 && selectedProfile != null
    }

    ConfirmDialog(dialogState)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                // Title bar
                TopAppBar(
                    title = { Text("Virtual Keys Settings") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                // Settings content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Enable/disable switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Enable Virtual Keys",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isControlsEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    selectedProfile = null
                                    showControls = false
                                    isControlsEnabled = false
                                    prefs.edit().remove(InputControlsFragment.SELECTED_PROFILE_ID).apply()
                                    prefs.edit().putBoolean("show_touchscreen_controls", false).apply()
                                } else if (profiles.isEmpty()) {
                                    val newProfile = manager.createProfile("Default Profile")
                                    profiles = manager.getProfiles()
                                    selectedProfile = newProfile
                                    showControls = true
                                    isControlsEnabled = true
                                    prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, newProfile.id).apply()
                                    prefs.edit().putBoolean("show_touchscreen_controls", true).apply()
                                } else {
                                    val savedId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)
                                    val restoredProfile = if (savedId != 0) manager.getProfile(savedId) else null
                                    if (restoredProfile != null) {
                                        selectedProfile = restoredProfile
                                    } else {
                                        selectedProfile = profiles.first()
                                        prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, selectedProfile!!.id).apply()
                                    }
                                    showControls = true
                                    isControlsEnabled = true
                                    prefs.edit().putBoolean("show_touchscreen_controls", true).apply()
                                }
                                onSettingsChanged()  // Notify caller to refresh
                            }
                        )
                    }

                    // Show/hide virtual keys switch
                    if (isControlsEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Show Virtual Keys",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = showControls,
                                onCheckedChange = { show ->
                                    showControls = show
                                    prefs.edit().putBoolean("show_touchscreen_controls", show).apply()
                                    onSettingsChanged()  // Notify caller to refresh
                                }
                            )
                        }
                    }

                    if (isControlsEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Profile selector
                        Text(
                            text = "Current Profile",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedProfile?.name ?: "Not selected",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.name) },
                                        onClick = {
                                            selectedProfile = profile
                                            prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, profile.id).apply()
                                            expanded = false
                                            onSettingsChanged()  // Notify caller to refresh
                                        }
                                    )
                                }
                            }
                        }

                        // Profile action buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("New")
                            }

                            if (profiles.size > 1) {
                                OutlinedButton(
                                    onClick = {
                                        selectedProfile?.let { profile ->
                                            manager.removeProfile(profile)
                                            profiles = manager.getProfiles()
                                            selectedProfile = profiles.firstOrNull()
                                            if (selectedProfile != null) {
                                                prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, selectedProfile!!.id).apply()
                                            } else {
                                                prefs.edit().remove(InputControlsFragment.SELECTED_PROFILE_ID).apply()
                                            }
                                            onSettingsChanged()  // Notify caller to refresh
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }
                        }

                        // Edit layout button
                        Button(
                            onClick = {
                                selectedProfile?.let { profile ->
                                    val intent = android.content.Intent(context, ControlsEditorActivity::class.java)
                                    intent.putExtra(ControlsEditorActivity.EXTRA_PROFILE_ID, profile.id)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Edit Virtual Keys Layout")
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // New profile dialog
    if (showProfileDialog) {
        var newProfileName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("New Profile") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            val newProfile = manager.createProfile(newProfileName)
                            profiles = manager.getProfiles()
                            selectedProfile = newProfile
                            prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, newProfile.id).apply()
                            showProfileDialog = false
                            onSettingsChanged()  // Notify caller to refresh
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * X11 settings floating popup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X11SettingsPopup(
    settingVm: SettingViewModel,
    onDismiss: () -> Unit
) {
    val x11State by settingVm.x11State.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                // Title bar
                TopAppBar(
                    title = { Text("X11 Display Settings") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                // Settings content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Resolution setting
                    X11Resolution(
                        text = settingVm.resolutionText,
                        onDone = settingVm::onChangeResolutionText
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Touch mode setting
                    X11TouchMode(
                        currentMode = x11State.touchMode,
                        onModeChange = settingVm::onChangeX11TouchMode
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Screen orientation setting
                    X11ScreenOrientation(
                        currentOrientation = x11State.screenOrientation,
                        onOrientationChange = settingVm::onChangeX11ScreenOrientation
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display scale
                    X11DisplayScale(
                        scale = x11State.displayScale,
                        onScaleChange = settingVm::onChangeX11DisplayScale
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Keep screen on
                    X11KeepScreenOn(
                        enabled = x11State.keepScreenOn,
                        onEnabledChange = settingVm::onChangeX11KeepScreenOn
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}