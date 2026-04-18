package org.github.ewt45.winemulator.ui


import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.FuncOnChangeAction
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.Utils
import org.github.ewt45.winemulator.emu.ProotRootfs
import org.github.ewt45.winemulator.permissions.RequiredPermissions
import org.github.ewt45.winemulator.ui.components.ConfirmDialog
import org.github.ewt45.winemulator.ui.components.ProgressDisplay
import org.github.ewt45.winemulator.ui.components.ProgressStage
import org.github.ewt45.winemulator.ui.components.TaskReporter
import org.github.ewt45.winemulator.ui.components.SimpleTaskReporter
import org.github.ewt45.winemulator.ui.components.rememberConfirmDialogState
import org.github.ewt45.winemulator.ui.components.rememberTaskReporter
import org.github.ewt45.winemulator.ui.setting.GeneralRootfsSelect_LoginUserSelect
import org.github.ewt45.winemulator.ui.setting.GeneralRootfsSelect_RootfsName
import org.github.ewt45.winemulator.viewmodel.PrepareViewModel
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import java.io.File

private val TAG = "PrepareScreen"

@Composable
fun PrepareScreen(prepareVm: PrepareViewModel, settingVm: SettingViewModel, navigateToMainScreen: suspend () -> Unit) {
    // Refresh state on first entry
    LaunchedEffect(Unit) {
        prepareVm.updateState()
    }
    PrepareScreenImpl(prepareVm, settingVm, navigateToMainScreen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreenImpl(prepareVm: PrepareViewModel, settingVm: SettingViewModel, navigateToMainScreen: suspend () -> Unit) {
    val state by prepareVm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Title will be set dynamically based on context
    val reporter = rememberTaskReporter(msgTitle = "")
    var autoExtractStarted by remember { mutableStateOf(false) } // Track whether auto-extraction has started

    // Set reporter title based on context
    LaunchedEffect(state.forceNoRootfs, state.noRootfs) {
        reporter.msgTitle = when {
            state.forceNoRootfs -> "New Container"
            state.noRootfs -> "Please select or extract a Rootfs"
            else -> ""
        }
    }

    // Exit PrepareScreen
    LaunchedEffect(state.isPrepareFinished) {
        if (!state.isPrepareFinished) return@LaunchedEffect
        // TODO: restart not yet implemented
        if (state.shouldRestart) MainEmuActivity.instance.finish()
        else navigateToMainScreen()

    }
    
    // On first launch (noRootfs), auto-attempt to extract rootfs from assets
    // When creating a new container (forceNoRootfs), skip auto-extract and let the user choose manually
    LaunchedEffect(state.skipPermissions, state.unGrantedPermissions.isEmpty()) {
        val permissionsReady = state.skipPermissions || state.unGrantedPermissions.isEmpty()
        if (permissionsReady && !autoExtractStarted && state.noRootfs && !state.forceNoRootfs) {
            autoExtractStarted = true
            reporter.msgTitle = "Auto-extracting rootfs..."
            reporter.stage = ProgressStage.PROCESSING
            reporter.progress = 0
            reporter.msg = "Logs:"
            
            try {
                val extractedRootfs = Utils.Rootfs.installRootfsFromAssets(ctx, reporter)
                if (extractedRootfs != null) {
                    reporter.msg("Auto-extracted rootfs successfully:${extractedRootfs.name}", "Auto-extraction successful!\n(Click logs to expand)")
                    reporter.stage = ProgressStage.DONE_SUCCESS
                    
                    // Auto-set startup command to linbox
                    settingVm.onChangeProotStartupCmd("linbox")
                    reporter.msg("Startup command set to: linbox")
                    
                    // Auto-set current rootfs (set symlink directly, avoid calling onChangeRootfsSelect to prevent triggering finish)
                    Utils.Rootfs.makeCurrent(extractedRootfs)
                    // Update state so UI can proceed
                    prepareVm.onRootfsExtracted(extractedRootfs.name)
                } else {
                    // No rootfs found in assets, falling back to manual selection
                    reporter.msg("No rootfs archive found in assets", "Please select a rootfs archive manually")
                    reporter.stage = ProgressStage.NOT_STARTED
                    autoExtractStarted = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                reporter.msg("Error during auto-extraction of rootfs:${e.stackTraceToString()}", "Auto-extraction failed. Please select a rootfs archive manually.\n(Click logs to expand)")
                reporter.stage = ProgressStage.DONE_FAILURE
                autoExtractStarted = false
            }
            reporter.progress = 100
        }
    }
    
    // When creating a new container, run extraction logic after user taps "Extract from App"
    LaunchedEffect(autoExtractStarted, state.forceNoRootfs) {
        if (autoExtractStarted && state.forceNoRootfs && reporter.stage == ProgressStage.NOT_STARTED) {
            reporter.msgTitle = "Extracting rootfs..."
            reporter.stage = ProgressStage.PROCESSING
            reporter.progress = 0
            reporter.msg = "Logs:"
            
            try {
                val extractedRootfs = Utils.Rootfs.installRootfsFromAssets(ctx, reporter)
                if (extractedRootfs != null) {
                    reporter.msg("Rootfs extracted successfully:${extractedRootfs.name}", "Extraction successful!\n(Click logs to expand)")
                    reporter.stage = ProgressStage.DONE_SUCCESS
                } else {
                    reporter.msg("No rootfs archive found in assets", "Please select a rootfs archive manually")
                    reporter.stage = ProgressStage.DONE_FAILURE
                    autoExtractStarted = false
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                reporter.msg("Error during rootfs extraction: ${e.stackTraceToString()}", "Extraction failed. Please select a rootfs archive manually.\n(Click logs to expand)")
                reporter.stage = ProgressStage.DONE_FAILURE
                autoExtractStarted = false
            }
            reporter.progress = 100
        }
    }

    // Preparation complete, launching emulator
    if (state.isPrepareFinished) {
        Box(Modifier.fillMaxSize()) {
            Text("Starting emulator...", Modifier.align(Alignment.Center))
        }
    }
    // Loading
    else if (state.loading) {
        Box(Modifier.fillMaxSize()) {
            Text("Loading...", Modifier.align(Alignment.Center))
        }
    }
    // Show appropriate content
    else {
        val lackPermissions = !(state.skipPermissions || state.unGrantedPermissions.isEmpty())
        var isRequestingPermission by remember { mutableStateOf(false) } // Prevent double-tapping grant button
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (lackPermissions) {
                PermissionGrant(isRequestingPermission, state.unGrantedPermissions, { prepareVm.onSkipPermissions() }) { permission ->
                    if (isRequestingPermission) return@PermissionGrant
                    isRequestingPermission = true
                    Utils.Permissions.request(permission.permission) { isGranted ->
                        if (isGranted) prepareVm.onGrantedPermission(permission);
                        isRequestingPermission = false
                    }
                }
            } else if (state.noRootfs || state.forceNoRootfs) {
                // On first launch with auto-extract, show progress (autoExtractStarted distinguishes auto vs manual)
                if (state.noRootfs && !state.forceNoRootfs && autoExtractStarted &&
                    (reporter.stage == ProgressStage.PROCESSING || 
                     reporter.stage == ProgressStage.DONE_SUCCESS)) {
                    RootfsAutoExtractProgress(reporter)
                }
                // Other cases (new container, auto-extract failed, manual select) show manual selection UI
                else if (state.forceNoRootfs || !autoExtractStarted || reporter.stage == ProgressStage.DONE_FAILURE) {
                    RootfsSelect(
                        getAvailableUsers = { rootfs: String -> ProotRootfs.getUserInfos(File(Consts.rootfsAllDir, rootfs)).map { it.name } },
                        settingVm::onChangeRootfsLoginUser, settingVm::onChangeRootfsName,
                        initReporter = reporter,
                        onAutoExtractStart = { autoExtractStarted = true },
                        onRootfsExtracted = { rootfsName -> prepareVm.onRootfsExtracted(rootfsName) },
                        onSetCurrentRootfs = { rootfsName -> Utils.Rootfs.makeCurrent(File(Consts.rootfsAllDir, rootfsName)) },
                        onCancel = if (state.forceNoRootfs) { { prepareVm.onCancelForceNoRootfs() } } else null,
                        defaultIsSetCurrent = !state.forceNoRootfs // Checked by default on first launch, unchecked for new container
                    )
                } else {
                    // Wait for auto-extraction to complete
                    Box(Modifier.fillMaxSize()) {
                        Text("Preparing...", Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

/**
 * Shows permissions the user should grant
 * @param onRequest Callback when the user taps the grant button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionGrant(
    isRequestingPermission: Boolean,
    permissions: List<RequiredPermissions>,
    onSkip: () -> Unit,
    onRequest: (RequiredPermissions) -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text("Permissions") },
        actions = { TextButton(onSkip) { Text("Skip") } }
    )
    Spacer(Modifier.height(16.dp))
    Column(Modifier.padding(16.dp)) {
        Text("To ensure the app works correctly, please grant the following permissions. Or tap \"Skip\" to proceed without granting them.")
        Spacer(Modifier.height(32.dp))
        permissions.forEach { item ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.bodyLarge)
                    if (item.description.isNotBlank())
                        Text(item.description, Modifier.padding(top = 8.dp))
                }
                Button({ onRequest(item) }, enabled = !isRequestingPermission) { Text("Grant") }
            }
            if (permissions.last() != item)
                HorizontalDivider(Modifier.padding(24.dp))
        }
    }
}


/**
 * @param getAvailableUsers Takes a rootfs name, returns the list of available users for that rootfs
 * @param onChangeUser See [SettingViewModel.onChangeRootfsLoginUser]
 * @param onRootfsNameChange See [SettingViewModel.onChangeRootfsName]
 * @param initReporter Optional initial task reporter
 * @param onAutoExtractStart Callback invoked when the user triggers auto-extraction
 * @param onRootfsExtracted Callback after rootfs extraction/selection completes, for updating state
 * @param onSetCurrentRootfs Callback to set the current rootfs
 * @param onCancel Cancel/back callback, used when creating a new container
 * @param defaultIsSetCurrent Default checked state of the "run this container on next launch" option; true on first launch, false for new container
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootfsSelect(
    getAvailableUsers: (String) -> List<String>,
    onChangeUser: suspend (String, String) -> Unit,
    onRootfsNameChange: suspend (String, String, FuncOnChangeAction) -> Unit,
    initStage: ProgressStage = ProgressStage.NOT_STARTED,
    initRootfsName: String = "",
    initReporter: SimpleTaskReporter? = null,
    onAutoExtractStart: (() -> Unit)? = null,
    onRootfsExtracted: ((String) -> Unit)? = null,
    onSetCurrentRootfs: (suspend (String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    defaultIsSetCurrent: Boolean = true,
) {
    val TAG = "RootfsSelectScreen"
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val reporter = initReporter ?: rememberTaskReporter(msgTitle = "Rootfs missing. Please tap the button to select a .tar.xz or .tar.gz archive containing a Rootfs.")
    var rootfsName by remember { mutableStateOf(initRootfsName) }
    var isSetCurrent by remember { mutableStateOf(defaultIsSetCurrent) }
    val dialogState = rememberConfirmDialogState()

    val readFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Log.d(TAG, "RootfsSelectScreen: trying to get mimetype from contentResolver? ${ctx.contentResolver.getType(uri)}")
        reporter.stage = ProgressStage.PROCESSING
        scope.launch {
            reporter.progress = 0
            reporter.msgTitle = "Extracting, please wait..."
            reporter.msg = "Logs:"
            try {
                rootfsName = Utils.Rootfs.installRootfsArchive(ctx, uri, reporter).name
                reporter.msg("Rootfs extracted successfully.", "Extraction successful. Click the button to exit. Please restart manually.\n(Click logs to expand)")
                reporter.stage = ProgressStage.DONE_SUCCESS
            } catch (e: Throwable) {
                e.printStackTrace()
                reporter.msg(
                    "Error during rootfs extraction.\n" + e.stackTraceToString(),
                    "Extraction failed. Please tap the button to select a .tar.xz or .tar.gz archive containing a Rootfs.\n(Click logs to expand)"
                )
                reporter.stage = ProgressStage.DONE_FAILURE
            }
            reporter.progress = 100
        }
    }

    ConfirmDialog(dialogState)

    CenterAlignedTopAppBar(
        title = { Text("Rootfs") },
        navigationIcon = {
            if (onCancel != null) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
    )
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
//        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Show title and progress
            ProgressDisplay(reporter)

            // Show selection buttons when extraction is needed
            if (reporter.stage == ProgressStage.NOT_STARTED || reporter.stage == ProgressStage.DONE_FAILURE) {
                // If auto-extract callback is available, offer auto-extract option
                if (onAutoExtractStart != null) {
                    Button({
                        onAutoExtractStart()
                        // Auto-extract logic is handled in PrepareScreenImpl
                    }) { Text("Extract from App") }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button({ readFileLauncher.launch(arrayOf("application/x-xz", "application/gzip", "*/*")) })
                { Text("Select Manually") }
            }
            // Show done button after successful extraction
            else if (reporter.stage == ProgressStage.DONE_SUCCESS) {
                Button({
                    scope.launch {
                        // Update prepareVm state
                        onRootfsExtracted?.invoke(rootfsName)
                        if (isSetCurrent && onSetCurrentRootfs != null) {
                            // Set current rootfs (avoid calling onChangeRootfsSelect to prevent triggering finish)
                            onSetCurrentRootfs(rootfsName)
                        } else if (isSetCurrent) {
                            // Fallback: if onSetCurrentRootfs not provided, use the old approach
                            MainEmuActivity.instance.settingViewModel.onChangeRootfsSelect(rootfsName)
                        }
                        // After state update, isPrepareFinished becomes true and navigation triggers automatically
                    }
                }) { Text("Done") }
            }

            // After successful extraction: rename, login user, set as current on next launch
            if (reporter.stage == ProgressStage.DONE_SUCCESS && rootfsName.isNotEmpty()) {
                Log.e(TAG, "RootfsSelectScreen: checking available login users after extraction. Should not normally reach here?")
                HorizontalDivider(Modifier.padding(16.dp), 2.dp)
                Text("Before exiting, you can also edit the following")

                var rootfsAlias by remember { mutableStateOf(Utils.Rootfs.getAlias(File(Consts.rootfsAllDir, rootfsName))) }
                GeneralRootfsSelect_RootfsName(
                    rootfsName = rootfsName,
                    rootfsAlias = rootfsAlias,
                    isCurr = false,
                    dialogState = dialogState,
                    onAliasChange = { _, newAlias ->
                        Utils.Rootfs.setAlias(File(Consts.rootfsAllDir, rootfsName), newAlias)
                        rootfsAlias = newAlias
                    }
                    // Not passing onRootfsNameChange — only modifying alias, not the folder name
                )

                val userList = getAvailableUsers(rootfsName)
                userList.find { it != "root" }?.let { nonRootUser ->
                    var userName by remember { mutableStateOf(nonRootUser) }
                    GeneralRootfsSelect_LoginUserSelect(rootfsName, userName, userList) { rootfsName, newUserName ->
                        userName = newUserName
                        scope.launch { onChangeUser(rootfsName, newUserName) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Run this container on next app launch")
                    Checkbox(isSetCurrent, { isSetCurrent = it })
                }
            }
        }
    }
}

/**
 * Progress display component for auto-extracting Rootfs
 */
@Composable
private fun RootfsAutoExtractProgress(reporter: SimpleTaskReporter) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Show title and progress
        ProgressDisplay(reporter)

        // Show message on success
        if (reporter.stage == ProgressStage.DONE_SUCCESS) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Rootfs extracted! Setting startup command...", style = MaterialTheme.typography.bodyLarge)
        }

        // Show message on failure
        if (reporter.stage == ProgressStage.DONE_FAILURE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Extraction failed. Please select a rootfs archive manually.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview
@Composable
fun PrepareScreenPreview(
    initLackPermissions: Boolean = true,
) {
    val isLoading = false
    val noRootfs = true
    val forceNoRootfs = false
    var unGrantedPermissions by remember {
        mutableStateOf(
            listOf<RequiredPermissions>(
                RequiredPermissions.Storage,
                RequiredPermissions.Notification
            )
        )
    }
    var skipPermissions by remember { mutableStateOf(false) }
    // Loading
    if (isLoading) {
        Box(Modifier.fillMaxSize()) {
            Text("Loading...", Modifier.align(Alignment.Center))
        }
    }
    // Show appropriate content
    else {
        val lackPermissions = !(skipPermissions || unGrantedPermissions.isEmpty()) && initLackPermissions
        var isRequestingPermission by remember { mutableStateOf(false) } // Prevent double-tapping grant button
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (lackPermissions) {
                PermissionGrant(isRequestingPermission, unGrantedPermissions, { skipPermissions = true }) { permission ->
                    if (!isRequestingPermission) {
                        isRequestingPermission = true
                        CoroutineScope(Dispatchers.Default).launch {
                            delay(1000)
                            unGrantedPermissions = unGrantedPermissions - permission
                            isRequestingPermission = false
                        }
                    }
                }
            } else if (noRootfs || forceNoRootfs) {
                val stage = ProgressStage.DONE_SUCCESS
                RootfsSelect({ listOf("iuser", "root") }, { _, _ -> }, { _, _, _ -> "" }, stage, "rootfs-1")
            }
        }
    }
}

//@Preview(widthDp = 300, heightDp = 600)
@Composable
private fun PrepareStageScreenFinishPreview() {
    val dialogState = rememberConfirmDialogState()
    ConfirmDialog(dialogState)
    ElevatedCard(Modifier.padding(16.dp)) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rootfsName = "rootfs-1"
            Text("Before exiting, you can also edit the following")

            Spacer(Modifier.height(16.dp))
            GeneralRootfsSelect_RootfsName(
                rootfsName = "rootfs-1",
                rootfsAlias = "rootfs-1",
                isCurr = false,
                dialogState = dialogState,
                onAliasChange = { _, _ -> },
                onRootfsNameChange = { _, _ -> }
            )

            val userList = listOf("root", "aid_u0_a287", "iuser").filter { !it.startsWith("aid_") }.sorted()
            val nonRootUser = userList.find { it != "root" }
            if (nonRootUser != null) {
                var userName by remember { mutableStateOf(nonRootUser) }
                Spacer(Modifier.height(16.dp))
                GeneralRootfsSelect_LoginUserSelect(rootfsName, userName, userList) { _, newUserName -> userName = newUserName }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Run this container on next app launch")
                Checkbox(true, {})
            }
        }
    }
}