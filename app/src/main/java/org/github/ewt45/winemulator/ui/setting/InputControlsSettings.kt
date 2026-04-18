package org.github.ewt45.winemulator.ui.setting

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.github.ewt45.winemulator.inputcontrols.ControlsProfile
import org.github.ewt45.winemulator.inputcontrols.InputControlsManager
import org.github.ewt45.winemulator.ui.ControlsEditorActivity
import org.github.ewt45.winemulator.ui.components.ConfirmDialog
import org.github.ewt45.winemulator.ui.components.rememberConfirmDialogState
import org.github.ewt45.winemulator.ui.InputControlsFragment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputControlsSettings(
    modifier: Modifier = Modifier
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

    // Load profiles and restore the last selected one
    LaunchedEffect(Unit) {
        manager.loadProfiles(ignoreTemplates = false)
        profiles = manager.getProfiles()
        val savedId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)
        // Only try to restore if savedId is non-zero; otherwise keep null
        selectedProfile = if (savedId != 0) manager.getProfile(savedId) else null
        // Ensure SharedPreferences has the correct ID
        if (selectedProfile != null && savedId != selectedProfile!!.id) {
            prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, selectedProfile!!.id).apply()
        }
        // Restore the virtual keys visibility state
        showControls = prefs.getBoolean("show_touchscreen_controls", false)
        // Restore enabled state — only enabled when there is a saved profile
        isControlsEnabled = savedId != 0 && selectedProfile != null
    }

    // Listen for SharedPreferences changes
    LaunchedEffect(prefs) {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "show_touchscreen_controls" -> {
                    showControls = prefs.getBoolean("show_touchscreen_controls", false)
                }
                InputControlsFragment.SELECTED_PROFILE_ID -> {
                    val savedId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)
                    isControlsEnabled = savedId != 0
                    selectedProfile = if (savedId != 0) manager.getProfile(savedId) else null
                }
            }
        }
    }

    // When the profiles list changes, disable virtual keys if the selected profile was deleted
    LaunchedEffect(profiles) {
        val savedId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)
        if (savedId != 0 && selectedProfile == null) {
            isControlsEnabled = false
        }
    }

    ConfirmDialog(dialogState)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Virtual key settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

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
                        // When disabling virtual keys, also hide them
                        selectedProfile = null
                        showControls = false
                        isControlsEnabled = false
                        prefs.edit().remove(InputControlsFragment.SELECTED_PROFILE_ID).apply()
                        prefs.edit().putBoolean("show_touchscreen_controls", false).apply()
                    } else if (profiles.isEmpty()) {
                        // Create a default profile
                        val newProfile = manager.createProfile("Default profile")
                        profiles = manager.getProfiles()
                        selectedProfile = newProfile
                        showControls = true
                        isControlsEnabled = true
                        prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, newProfile.id).apply()
                        prefs.edit().putBoolean("show_touchscreen_controls", true).apply()
                    } else {
                        // Try to restore the previously saved profile rather than forcing the first one
                        val savedId = prefs.getInt(InputControlsFragment.SELECTED_PROFILE_ID, 0)
                        val restoredProfile = if (savedId != 0) manager.getProfile(savedId) else null
                        if (restoredProfile != null) {
                            selectedProfile = restoredProfile
                        } else {
                            // Only fall back to the first profile when no saved one can be restored
                            selectedProfile = profiles.first()
                            prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, selectedProfile!!.id).apply()
                        }
                        // When enabling virtual keys, also show them
                        showControls = true
                        isControlsEnabled = true
                        prefs.edit().putBoolean("show_touchscreen_controls", true).apply()
                    }
                }
            )
        }

        // Show/hide virtual keys switch (only visible when virtual keys are enabled)
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
                    }
                )
            }
        }

        if (isControlsEnabled) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Profile selector
            Text(
                text = "Current profile",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedProfile!!.name,
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

            OutlinedButton(
                onClick = {
                    selectedProfile?.let { profile ->
                        selectedProfile = manager.duplicateProfile(profile)
                        profiles = manager.getProfiles()
                        prefs.edit().putInt(InputControlsFragment.SELECTED_PROFILE_ID, selectedProfile!!.id).apply()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Duplicate current profile")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Edit layout button
            Button(
                onClick = {
                    selectedProfile?.let { profile ->
                        val intent = Intent(context, ControlsEditorActivity::class.java)
                        intent.putExtra(ControlsEditorActivity.EXTRA_PROFILE_ID, profile.id)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Edit virtual key layout")
            }

            // Quick presets (demo)
            Text(
                text = "Quick Presets",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val presetNames = listOf(
                    "FPS Game", "RPG Game", "RTS Game", "Racing Game", "Fighting Game"
                )

                items(presetNames) { presetName ->
                    ListItem(
                        headlineContent = { Text(presetName) },
                        leadingContent = {
                            Icon(Icons.Default.Star, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            dialogState.showConfirm("Load preset '$presetName'? This will replace the current profile's key layout.") {
                                // Apply preset logic (implementation depends on project)
                            }
                        }
                    )
                }
            }

            // Export / import
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedProfile?.let { profile ->
                            val file = manager.exportProfile(profile)
                            if (file != null) {
                                dialogState.showConfirm("Profile exported to: ${file.absolutePath}")
                            } else {
                                dialogState.showConfirm("Export failed")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }

                OutlinedButton(
                    onClick = {
                        // Import requires a file picker; this is a demo placeholder
                        dialogState.showConfirm("Please select the .icp file to import")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
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
                    label = { Text("Profile name") },
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
 * Dialog for editing control elements
 */
@Composable
fun ControlsEditorDialog(
    profile: ControlsProfile,
    dialogState: org.github.ewt45.winemulator.ui.components.ConfirmDialogState,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var selectedElement by remember { mutableStateOf<org.github.ewt45.winemulator.inputcontrols.ControlElement?>(null) }
    var showElementSettings by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Virtual Keys - ${profile.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: Tap the buttons below to add new control elements. Long-press and drag to reposition them.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Element list
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items(profile.getElements()) { element ->
                        ListItem(
                            headlineContent = {
                                Text("${element.type.name} - ${element.getBindingAt(0).name}")
                            },
                            supportingContent = {
                                Text("Position: (${element.x}, ${element.y})")
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = when (element.type) {
                                        org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.BUTTON -> Icons.Default.Star
                                        org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.D_PAD -> Icons.AutoMirrored.Filled.ArrowForward
                                        org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.STICK -> Icons.Default.Info
                                        org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.RANGE_BUTTON -> Icons.AutoMirrored.Filled.List
                                        org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.TRACKPAD -> Icons.Default.Menu
                                    },
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    profile.removeElement(element)
                                    profile.save()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            },
                            modifier = Modifier.clickable {
                                selectedElement = element
                                showElementSettings = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add element buttons
                Text(
                    text = "Add Control",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            // Should call the actual add logic once implemented
                            dialogState.showConfirm("Long-press the screen in-game to add a new virtual key")
                        },
                        label = { Text("Button") },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )

                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text("D-Pad") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                    )

                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text("Joystick") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Element settings dialog
    if (showElementSettings && selectedElement != null) {
        ElementSettingsDialog(
            element = selectedElement!!,
            onDismiss = { showElementSettings = false },
            onSave = {
                profile.save()
                showElementSettings = false
            }
        )
    }
}

/**
 * Dialog for editing a single control element
 */
@Composable
fun ElementSettingsDialog(
    element: org.github.ewt45.winemulator.inputcontrols.ControlElement,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var selectedType by remember { mutableStateOf(element.type) }
    var selectedShape by remember { mutableStateOf(element.shape) }
    var scale by remember { mutableStateOf(element.scale) }
    var text by remember { mutableStateOf(element.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type selector
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.entries.forEach { type ->
                        FilterChip(
                            selected = element.type == type,
                            onClick = {
                                element.type = type
                                selectedType = type
                            },
                            label = { Text(type.name.replace("_", "-")) }
                        )
                    }
                }

                // Shape selector (for buttons)
                if (element.type == org.github.ewt45.winemulator.inputcontrols.ControlElement.Type.BUTTON) {
                    Text("Shape", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        org.github.ewt45.winemulator.inputcontrols.ControlElement.Shape.entries.forEach { shape ->
                            FilterChip(
                                selected = element.shape == shape,
                                onClick = {
                                    element.shape = shape
                                    selectedShape = shape
                                },
                                label = { Text(shape.name.replace("_", " ")) }
                            )
                        }
                    }
                }

                // Scale slider
                Text("Size: ${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = scale,
                    onValueChange = {
                        scale = it
                        element.scale = it
                    },
                    valueRange = 0.5f..2f
                )

                // Custom text
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        element.text = it
                    },
                    label = { Text("Custom text (optional)") },
                    singleLine = true
                )

                // Binding section
                Text("Key Binding", style = MaterialTheme.typography.labelMedium)
                Column {
                    for (i in 0 until element.getBindingCount()) {
                        val binding = element.getBindingAt(i)
                        ListItem(
                            headlineContent = { Text("Binding ${i + 1}") },
                            supportingContent = { Text(binding.name) },
                            modifier = Modifier.clickable {
                                // Binding picker would open here; not yet implemented
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}