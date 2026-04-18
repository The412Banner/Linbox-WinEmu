package org.github.ewt45.winemulator.ui.setting

import a.io.github.ewt45.winemulator.R
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.CompressedType
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.FuncOnChange
import org.github.ewt45.winemulator.FuncOnChangeAction
import org.github.ewt45.winemulator.Utils
import org.github.ewt45.winemulator.emu.ProotRootfs
import org.github.ewt45.winemulator.ui.AnimatedVertical
import org.github.ewt45.winemulator.ui.components.CollapsePanel
import org.github.ewt45.winemulator.ui.components.ComposeSpinner
import org.github.ewt45.winemulator.ui.Destination
import org.github.ewt45.winemulator.ui.components.ProgressStage
import org.github.ewt45.winemulator.ui.components.TextFieldOption
import org.github.ewt45.winemulator.ui.components.TitleAndContent
import org.github.ewt45.winemulator.ui.components.ConfirmDialog
import org.github.ewt45.winemulator.ui.components.ConfirmDialogState
import org.github.ewt45.winemulator.ui.components.ProgressDisplay
import org.github.ewt45.winemulator.ui.components.TaskReporter
import org.github.ewt45.winemulator.ui.components.rememberConfirmDialogState
import org.github.ewt45.winemulator.ui.components.rememberTaskReporter
import org.github.ewt45.winemulator.viewmodel.PrepareViewModel
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Random

private val TAG = "GeneralSettings"


@Composable
fun GeneralSettings(
    settingVM: SettingViewModel,
    prepareVm: PrepareViewModel,
    navigateTo: (Destination) -> Unit,
) {

    val state by settingVM.generalState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()


    CollapsePanel("General Options", vPadding = 32.dp) {
        // Resolution setting has been moved to X11 Display Settings
        // GeneralResolution(settingVM.resolutionText, settingVM::onChangeResolutionText)
        GeneralRootfsLang(state.rootfsLang, listOf("en_US.utf8", "zh_CN.utf8"), settingVM::onChangeRootfsLang)
        GeneralShareDir(state.sharedExtPath, settingVM::onChangeShareExtPath)
//        MoreContent {

        GeneralRootfsSelect(
            Consts.rootfsCurrDir.canonicalFile.name,
            state.localRootfsLoginUsersMap,
            settingVM.rootfsUsersOptions.value.mapValues { it.value.map { info -> info.name } },
            settingVM.rootfsAliasMap.value,
            settingVM::onChangeRootfsName,
            settingVM::onChangeRootfsSelect,
            { rootfs, user -> scope.launch { settingVM.onChangeRootfsLoginUser(rootfs, user) } },
            settingVM::onChangeRootfsAlias,
            { prepareVm.setForceNoRootfs() },
        )
//        }
    }
}

/**
 * Theme mode selector.
 * 0 = Follow System, 1 = Dark Theme, 2 = Light Theme
 */
@Composable
fun GeneralThemeMode(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
) {
    val themeOptions = listOf("Follow System", "Dark Theme", "Light Theme")
    val themeModeNames = mapOf(0 to "Follow System", 1 to "Dark Theme", 2 to "Light Theme")
    val currentThemeName = themeModeNames[themeMode] ?: "Dark Theme"
    
    TitleAndContent("Theme Mode", "Select the UI theme. Dark theme is used by default.") {
        ComposeSpinner(currentThemeName, themeOptions, modifier = Modifier.fillMaxWidth()) { _, new ->
            val newMode = themeOptions.indexOf(new)
            if (newMode >= 0) {
                onThemeModeChange(newMode)
            }
        }
    }
}


@Composable
fun GeneralRootfsLang(
    currLang: String,
    langOptions: List<String>,
    onLangChange: (String) -> Unit,
) {
    TitleAndContent("Container Language", "Value of the LANG environment variable when starting the container.") {
        ComposeSpinner(currLang, langOptions, modifier = Modifier.fillMaxWidth()) { _, new -> onLangChange(new) }
    }
}


/**
 * Rootfs switcher, delete, rename, and add.
 * @param currRootfs Name of the currently running rootfs (an entry in rootfsList, the real path pointed to by [Consts.rootfsCurrDir]). This entry should be disabled.
 * @param onRootfsNameChange Called when a folder is renamed or deleted
 * @param rootfsToLoginUserMap Login username for each rootfs. See [Consts.Pref.Local.rootfs_login_user_json]. Ensure every rootfs has a key and its value satisfies [ProotRootfs.getPreferredUser].
 * @param loginUsersOptions All available usernames for each rootfs. Ensure the list excludes [Consts.rootfsCurrDir].
 * @param rootfsAliasMap Alias map: key = folder name, value = alias
 * @param onRootfsSelectChange Called when the active rootfs changes
 * @param onUserSelectChange Called when a rootfs's login user changes. Param 1 = rootfs name, param 2 = username
 */
@Composable
fun GeneralRootfsSelect(
    currRootfs: String,
    rootfsToLoginUserMap: Map<String, String>,
    loginUsersOptions: Map<String, List<String>>,
    rootfsAliasMap: Map<String, String>,
    onRootfsNameChange: suspend (String, String, FuncOnChangeAction) -> Unit,
    onRootfsSelectChange: suspend (String) -> Unit,
    onUserSelectChange: (String, String) -> Unit,
    onAliasChange: (String, String) -> Unit,
    navigateToNewRootfs: () -> Unit,
) {

    val scope = rememberCoroutineScope()
//    TODO After sorting, it works fine. Previously, renaming with list.minus.plus shifted the rootfs entry up by one position while the username stayed — what was the root cause?
    val sortedRootfsList = loginUsersOptions.keys.sortedWith(compareBy<String> { it != currRootfs }.thenBy { it })
    val dialogState = rememberConfirmDialogState()

    val TYPE_SEL = 0 // switch
    val TYPE_DEL = 1 // delete
    fun onClickBtn(type: Int, rootfsName: String, rootfsAlias: String, isCurr: Boolean, newRootfsName: String? = null) = scope.launch {
        if (type == TYPE_SEL && !isCurr) {
            dialogState.showConfirm("Set this folder as the rootfs for Proot?\nThe app will exit after confirming. Please restart manually.\n\n$rootfsAlias") {
                onRootfsSelectChange(rootfsName)
            }
        } else if (type == TYPE_DEL) {
            // If this is the currently running rootfs, just show a message and skip deletion
            if (isCurr) {
                dialogState.showConfirm("This rootfs is currently running and cannot be deleted.\n\n$rootfsAlias")
            } else {
                dialogState.showConfirm("Are you sure you want to delete this rootfs?\nAll files inside will be permanently deleted!\n\n$rootfsAlias") {
                    onRootfsNameChange(rootfsName, rootfsName, FuncOnChangeAction.DEL)
                }
            }
        }
    }

    ConfirmDialog(dialogState)

    TitleAndContent("Rootfs Manager", "Switch, add, rename, or delete the rootfs used by PRoot. Changes take effect after restarting the app.") {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(onClick = navigateToNewRootfs) { Icon(Icons.Filled.Add, null) }

            for (rootfsName in sortedRootfsList) {
                val isCurr = rootfsName == currRootfs
                val rootfsAlias = rootfsAliasMap[rootfsName] ?: rootfsName
                val userNameOptions = loginUsersOptions[rootfsName]
                val userName = rootfsToLoginUserMap[rootfsName]
                if (userNameOptions == null || userName == null) {
                    //TODO No loading-state mechanism yet (sealed interface), so initial values may be stale — skip for now; accurate data should arrive shortly.
                    continue
                }
                if (!userNameOptions.contains(userName)) {
                    throw RuntimeException("loginUsersOptions must contain userName! loginUsersOptions=$userNameOptions, rootfsName=$rootfsName, username=$userName")
                }

                Box {
                    Row(Modifier.padding(0.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1F)) {
                            // Settings page: only update the alias, not the container folder name
                            GeneralRootfsSelect_RootfsName(rootfsName, rootfsAlias, isCurr, dialogState, onAliasChange)
                            Spacer(Modifier.height(8.dp))
                            GeneralRootfsSelect_LoginUserSelect(rootfsName, userName, userNameOptions, onUserSelectChange)
                        }

                        Spacer(Modifier.width(8.dp))
                        OutlinedCard(
                            shape = RoundedCornerShape(100.dp),
                            border = CardDefaults.outlinedCardBorder(),
                        ) {
                            val btnModifier = Modifier.size(40.dp)//.padding(4.dp)
                            Column {
                                IconButton(onClick = { onClickBtn(TYPE_SEL, rootfsName, rootfsAlias, isCurr) }, btnModifier) {
                                    if (isCurr) Icon(Icons.Filled.Check, null)
                                    else Icon(painterResource(R.drawable.ic_switch), null)
                                }
                                IconButton(onClick = { onClickBtn(TYPE_DEL, rootfsName, rootfsAlias, isCurr) }, btnModifier)
                                { Icon(Icons.Filled.Delete, null) }
                                GeneralRootfsSelect_ExportRootfs(btnModifier, rootfsName)
                            }
                        }
                    }
                }
                if (sortedRootfsList.last() != rootfsName) {
                    HorizontalDivider(Modifier.padding(8.dp))
                }
            }
        }
    }
}

/**
 * Sub-layout of [GeneralRootfsSelect] — exports a rootfs as a compressed archive.
 */
@Composable
fun GeneralRootfsSelect_ExportRootfs(modifier: Modifier = Modifier, rootfsName: String) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = {
        showDialog = true
    }, modifier)
    { Icon(painterResource(R.drawable.ic_archive_save), null) }

    fun getMimeType(extension: String) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    if (showDialog) {
        var currCompType by remember { mutableStateOf(CompressedType.TZST) }
        val compSuffix = mapOf(
            CompressedType.GZ to ".tar.gz", 
            CompressedType.XZ to ".tar.xz",
            CompressedType.TZST to ".tar.zst"
        )
        val compMimeTypes = mapOf(
            CompressedType.GZ to "application/gzip", 
            CompressedType.XZ to "application/x-xz",
            CompressedType.TZST to "application/zstd"
        )
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val reporter = rememberTaskReporter(msgTitle = "Export Rootfs: $rootfsName to an archive for future restore or use elsewhere.")

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(compMimeTypes[currCompType]!!)) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            reporter.stage = ProgressStage.PROCESSING
            reporter.progress = 0
            reporter.msgTitle = "Compressing"
            reporter.msg = "Log:"
            scope.launch {
                try {
                    Utils.Rootfs.exportRootfsArchive(ctx, uri, File(Consts.rootfsAllDir, rootfsName), currCompType, reporter)
                    reporter.msg("Export successful.", "Export successful! Saved to the specified directory.\n(Tap logs to expand)")
                    reporter.stage = ProgressStage.DONE_SUCCESS
                } catch (e: Exception) {
                    e.printStackTrace()
                    reporter.msg("Error during rootfs compression:${e.stackTraceToString()}", "Export failed!\n(Tap logs to expand)")
                    reporter.stage = ProgressStage.DONE_FAILURE
                }
            }
        }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { },
            dismissButton = { },
            icon = { Icon(painterResource(R.drawable.ic_archive_save), null) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProgressDisplay(reporter)
                    Spacer(Modifier.height(16.dp))
                    // Export settings are only available before the operation starts
                    if (reporter.stage == ProgressStage.NOT_STARTED) {
                        ComposeSpinner(currCompType, compSuffix.keys.toList(), compSuffix.values.toList(), label = "Compression Format")
                        { _, new -> currCompType = new }
                        Spacer(Modifier.height(24.dp))
                        Button({
                            // Append timestamp to avoid filename conflicts (Android inserts the counter mid-name: .tar(1).gz)
                            val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm-ss"))
                            launcher.launch("${rootfsName}_$timeStr${compSuffix[currCompType]!!}")
                        }) { Text("Export to...") }
                        TextButton({ showDialog = false }) { Text("Cancel") }
                    }
                    // Show close button when compression finishes
                    if (reporter.stage == ProgressStage.DONE_SUCCESS || reporter.stage == ProgressStage.DONE_FAILURE) {
                        Button({ showDialog = false }) { Text("Close") }
                    }

                }
            }
        )
    }
}

/**
 * Sub-layout of [GeneralRootfsSelect] — selects the login user for a rootfs.
 */
@Composable
fun GeneralRootfsSelect_LoginUserSelect(
    rootfsName: String,
    userName: String,
    userNameOptions: List<String>,
    onUserSelectChange: (String, String) -> Unit,
) {
    ComposeSpinner(userName, userNameOptions, label = "Login Username", modifier = Modifier.fillMaxWidth()) { _, newValue ->
        onUserSelectChange(rootfsName, newValue)
    }
}

/**
 * Sub-layout of [GeneralRootfsSelect] — edits the alias for a rootfs.
 * @param rootfsName rootfs folder name
 * @param rootfsAlias current alias
 * @param onAliasChange called when the alias changes; pass an empty lambda if alias editing is not needed (e.g. post-import screen)
 * @param onRootfsNameChange called when the container folder name changes; used on the post-import screen
 */
@Composable
fun GeneralRootfsSelect_RootfsName(
    rootfsName: String,
    rootfsAlias: String,
    isCurr: Boolean,
    dialogState: ConfirmDialogState,
    onAliasChange: (String, String) -> Unit,
    onRootfsNameChange: suspend (String, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    
    TextFieldOption(rootfsAlias, title = "Rootfs Name", outlined = true) {
        val newName = it.trim()
        if (newName.isBlank() || newName == rootfsAlias) return@TextFieldOption
        
        // Update alias (settings page)
        onAliasChange(rootfsName, newName)

        // Update container folder name (post-import screen)
        // Only called when onRootfsNameChange is not the default no-op
        scope.launch {
            onRootfsNameChange(rootfsName, newName)
        }
    }
}

/**
 * Shows a “More...” button that expands additional content when clicked.
 */
@Composable
fun MoreContent(modifier: Modifier = Modifier, btnText: String = “More...”, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    var isShowContent by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        AnimatedVertical(isShowContent, content = content)
        if (!isShowContent)
            TextButton(
                onClick = { isShowContent = !isShowContent },
                Modifier.align(Alignment.Center)
            ) { Text(btnText) }
    }
}

//FIXME If one shared directory is a subdirectory of another, the symlink cannot be created
/**
 * Binds external shared folders.
 * @param onPathChange parameters: path and whether it is a delete operation
 */
@Composable
fun GeneralShareDir(
    bindSet: Set<String>,
    onPathChange: FuncOnChange<String>,//suspend (String, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bindList = bindSet.sorted()
    val dialogState = rememberConfirmDialogState()
    val selectFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        Log.d(TAG, "GeneralShareDir: got uri $uri \npath=${uri.path}")
        val path = uri.path?.split(":", limit = 2)?.get(1)
        val fullPath = if (path != null) "/storage/emulated/0/$path" else ""
        scope.launch {
            if (fullPath.isEmpty()) {
                dialogState.showConfirm("Add failed! Cannot get folder path.\n\nuri: $uri")
            } else if (!File(fullPath).exists()) {
                dialogState.showConfirm("Add failed! The folder does not exist.\n\npath: $fullPath \n\nuri: $uri")
            } else {
                onPathChange(fullPath, fullPath, FuncOnChangeAction.ADD)
            }
        }
    }

    ConfirmDialog(dialogState)

    TitleAndContent("Shared Folder", "Add Android folders here. They will be accessible inside the container after the emulator starts.") {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalIconButton(onClick = { selectFolder.launch(null) }) {
                Icon(Icons.Filled.Add, null)
            }
            for (bind in bindList) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { }, verticalAlignment = Alignment.CenterVertically
                ) {
                    TextFieldOption(bind, Modifier.weight(1F), outlined = true) { newPath ->
                        scope.launch {
                            if (!File(newPath).exists())
                                dialogState.showConfirm("Add failed! The folder does not exist.\n\npath: $newPath")
                            else
                                onPathChange(bind, newPath, FuncOnChangeAction.EDIT)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            dialogState.showConfirm("Are you sure you want to unshare this folder?\n\n$bind") {
                                onPathChange(bind, bind, FuncOnChangeAction.DEL)
                            }
                        }
                    }, Modifier.size(32.dp)) { Icon(Icons.Filled.Clear, null) }
                }
            }
        }
    }

}

/** Resolution picker */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralResolution(
    text: String,
    onDone: (String, Boolean) -> Unit,
) {
    val options = listOf("800x600", "1024x768", "1280x720", "1600x900", "1920x1080")
    val textInOptions = options.contains(text)
    // isCustom starts based on whether the resolution is in the preset list; can be toggled manually afterward
    var isCustom by remember { mutableStateOf(!textInOptions) }
    val realText = if (isCustom) "Custom" else text
    var expanded by remember { mutableStateOf(false) }

    // User taps “Custom” -> callback sets isCustom=true -> onDone is not called in that case
    // TextField shows “Custom” when isCustom, otherwise shows the incoming resolution
    // TextField onValueChange is a no-op; ViewModel updates happen in the option-click callbacks

    TitleAndContent("Resolution", "Format: widthxheight (x is the letter). After editing a custom resolution, tap the checkmark icon or press Enter to save.") {
        ExposedDropdownMenuBox(
            modifier = Modifier.fillMaxWidth(),
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
//                .focusRequester(focusRequester)
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
                //TODO Add aspect-ratio selection
//            Row(modifier = Modifier
//                .padding(contentPadding)
//                .horizontalScroll(ScrollState(0))
//            ) {
//                TextButton(onClick = {}) { Text("4:3") }
//                TextButton(onClick = {}) { Text("16:9") }
//                TextButton(onClick = {}) { Text("9:16") }
//            }
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

        // Text field shown for custom resolution input
        AnimatedVertical(isCustom) {
            TextFieldOption(text = text, onDone = { onDone(it, true) })
        }
    }
}


@Preview(widthDp = 300, heightDp = 600)
@Composable
fun GeneralSettingsPreview() {
    val langOptions = listOf("en_US.utf8", "zh_CN.utf8")
    var lang by remember { mutableStateOf(langOptions[0]) }
    var shareDirSet by remember { mutableStateOf(setOf("/storage/emulated/0/Download", "/storage/emulated/0/MT2")) }
    val onChangeShareDir: FuncOnChange<String> = { old, new, action ->
        if (action == FuncOnChangeAction.DEL) shareDirSet -= new
        if (action == FuncOnChangeAction.ADD) shareDirSet += "/added/path/${Random(1).nextInt()}"
        if (action == FuncOnChangeAction.EDIT) shareDirSet = shareDirSet - old + new
    }
    val loginUsersOptions = mapOf("rootfs-1" to listOf("root"), "rootfs-2" to listOf("iuser", "root"), "rootfs-3" to listOf("iuser_3", "root"))
    val rootfsToLoginUserMap = loginUsersOptions.mapValues { entry -> entry.value.run { find { it != "root" } ?: find { it == "root" }!! } }
    CollapsePanel("General Options") {
//        GeneralResolution("1280x720", { _, _ -> })
//        GeneralRootfsLang(lang, langOptions, { lang = it })
//        GeneralShareDir(shareDirSet, onChangeShareDir)
        GeneralRootfsSelect(
            currRootfs = "rootfs-3",
            rootfsToLoginUserMap = rootfsToLoginUserMap,
            loginUsersOptions = loginUsersOptions,
            rootfsAliasMap = emptyMap(),
            onRootfsNameChange = { _, _, _ -> },
            onRootfsSelectChange = { },
            onUserSelectChange = { _, _ -> },
            onAliasChange = { _, _ -> },
            navigateToNewRootfs = { }
        )
    }
}

