package org.github.ewt45.winemulator.ui

import a.io.github.ewt45.winemulator.R
import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import org.github.ewt45.winemulator.Consts
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.Utils.Ui.snapToNearestEdgeHalfway
import org.github.ewt45.winemulator.ui.theme.MainTheme
import org.github.ewt45.winemulator.viewmodel.DialogType
import org.github.ewt45.winemulator.viewmodel.MainUiState
import org.github.ewt45.winemulator.viewmodel.MainViewModel
import org.github.ewt45.winemulator.viewmodel.PrepareViewModel
import org.github.ewt45.winemulator.viewmodel.SettingViewModel
import org.github.ewt45.winemulator.viewmodel.TerminalViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    tx11Content: (Context) -> View,
    startDest: Destination,
    mainVm: MainViewModel,
    terminalVm: TerminalViewModel,
    settingVm: SettingViewModel,
    prepareVm: PrepareViewModel,
) {
    val TAG = "MainScreen"
    val navController = rememberNavController()

    val uiState by mainVm.uiState.collectAsState()
    val prepareUiState by prepareVm.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currDestination = appbarDestList.find { navBackStackEntry?.destination?.hasRoute(it.route::class) == true } ?: startDest

    // Navigate to a destination. If it's already in the back stack, pop up to it first.
    val navigateTo: (Destination) -> Unit = { navController.navigate(it.route) { popUpTo(it.route) { inclusive = true } } }

    // Trigger navigation when the Activity updates the destination via the ViewModel
    LaunchedEffect(Unit) {
        mainVm.navigateToEvent.collect { dest -> navigateTo(dest) }
    }
    // Auto-navigate to X11 on startup when auto-start is configured
    LaunchedEffect(prepareUiState.isPrepareFinished) {
        if (prepareUiState.isPrepareFinished && startDest == Destination.X11) {
            // Preparation done and target is X11 — navigate automatically
            navigateTo(Destination.X11)
        }
    }
    // Navigate to the prepare screen at startup or mid-session when preparation is pending
    LaunchedEffect(prepareUiState.isPrepareFinished) {
        if (!prepareUiState.isPrepareFinished && currDestination != Destination.Prepare)
            navigateTo(Destination.Prepare)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MyTopAppBar(currDestination, { navigateTo(it) })
        },
    ) { innerPadding ->
        // FIXME: tx11 already handles keyboard height changes, so innerPadding shouldn't be needed here (causes blank space),
        //  but the Scaffold topBar requires applying the top padding
//            val ignoreSystemInsets = Modifier.padding(innerPadding)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.Center,
        ) {
//            Column(Modifier.fillMaxHeight().widthIn(max = 600.dp)) { }
            NavHost(
                navController, startDest.route,
                enterTransition = { scaleIn() },
                exitTransition = { scaleOut() },
            ) {
                composable<RoutePrepare> {
                    PrepareScreen(prepareVm, settingVm) {
                        MainEmuActivity.instance.startEmu()
                        // Navigate directly to X11 once preparation is complete
                        navController.navigate(Destination.X11.route) { popUpTo(Destination.Prepare.route) { inclusive = true } }
                    }
                }
                composable<RouteX11> { X11Screen(tx11Content, navigateTo, settingVm = settingVm) }
                navigation<RouteExceptX11>(startDestination = RouteTerminal) {
                    composable<RouteTerminal> { ProotTerminalScreen(terminalVm) }
//                        composable<NavDest.Terminal> { TerminalScreen() }
                    composable<RouteSettings> { SettingScreen(settingVm, terminalVm, prepareVm, navigateTo) }
                }
            }
        }

        MainDialog(uiState) { mainVm.closeConfirmDialog(it) }

    }
}

@Composable
private fun MainDialog(uiState: MainUiState, onClose: (Boolean) -> Unit) {
    // Dialog
    val dialogType = uiState.dialogType
    val isConfirm = uiState.dialogType == DialogType.CONFIRM
    val isBlock = uiState.dialogType == DialogType.BLOCK
    if (dialogType != DialogType.NONE) {
        AlertDialog(
            onDismissRequest = {}, // prevent dismissal on outside tap
//                title = { Text("Loading") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SelectionContainer {
                        Text(uiState.msg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                    if (isBlock) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                if (isConfirm) {
                    TextButton(onClick = { onClose(true) }) { Text(stringResource(android.R.string.ok)) }
                }
            },
            dismissButton = {
                if (isConfirm) {
                    TextButton(onClick = { onClose(false) }) { Text(stringResource(android.R.string.cancel)) }
                }
            }
        )
    }
}


/**
 * Top AppBar with a scrollable tab row of navigation destinations.
 * Hidden when the current destination is not in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyTopAppBar(
    currDestination: Destination?,
    setDestination: (Destination) -> Unit,
) {
    val selectIdx = currDestination?.let { appbarDestList.indexOf(it) }
    if (selectIdx != null && selectIdx != -1) {
        TopAppBar(
            title = {
                // TODO: convert to Navigation — see https://developer.android.com/develop/ui/compose/components/tabs
                PrimaryScrollableTabRow(selectIdx, divider = {}) {
                    appbarDestList.forEachIndexed { idx, dest ->
                        Tab(
                            selected = selectIdx == idx,
                            onClick = { setDestination(dest) },
                            text = {
                                Text(
                                    text = dest.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        )
                    }
                }
            },
            actions = {
                IconButton({ setDestination(Destination.X11) }) { Icon(painterResource(R.drawable.ic_hide), null) }
            },
//        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
        )
    }

}

/**
 * Button that expands or collapses the Compose overlay.
 * Draggable: since the X11 activity is a View, dragging is implemented via View LayoutParams.
 */
@Composable
private fun MinimizeButton(
    minimize: Boolean,
    onClick: () -> Unit,
) {
    val TAG = "MinimizeButton"
    val activity = LocalActivity.current
    val miniIconPx = (Consts.Ui.minimizedIconSize * LocalDensity.current.density).toInt()

    // Tint the button slightly when minimized so it remains visible
    val colorSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val colorContent = MaterialTheme.colorScheme.onSurface
    val colors =
        if (!minimize) IconButtonDefaults.iconButtonColors()
        else IconButtonColors(colorSurface, colorContent, colorSurface, colorContent)

    // Remember the minimized position so it restores to the last position rather than the default
    val margin = remember { mutableListOf(0, 100) }

    IconButton(
        onClick = {
            val view = activity?.findViewById<View>(R.id.compose_view) ?: return@IconButton
            val nextValue = !minimize
            view.apply {
                val lp = layoutParams as MarginLayoutParams
                lp.height = if (nextValue) miniIconPx else MATCH_PARENT
                lp.width = if (nextValue) miniIconPx else MATCH_PARENT
                lp.leftMargin = if (nextValue) margin[0] else 0
                lp.topMargin = if (nextValue) margin[1] else 0
                lp.rightMargin = 0
                lp.bottomMargin = 0
                requestLayout()
                if (nextValue)
                    view.post { view.snapToNearestEdgeHalfway() }
            }
            onClick()
        },
        modifier = Modifier
            .size(Consts.Ui.minimizedIconSize.dp)
            .pointerInput(minimize) {
                if (!minimize)
                    return@pointerInput
                val view = activity?.findViewById<View>(R.id.compose_view) ?: return@pointerInput
                detectDragGestures(
                    onDragEnd = { view.snapToNearestEdgeHalfway() }
                ) { change, dragAmount ->
                    change.consume()
                    val lp = view.layoutParams as MarginLayoutParams
                    lp.leftMargin += dragAmount.x.toInt()
                    lp.topMargin += dragAmount.y.toInt()
                    margin[0] = lp.leftMargin
                    margin[1] = lp.topMargin
                    view.requestLayout()
                }
            },
        colors = colors
    ) {
        Icon(
            painter = painterResource(if (minimize) R.drawable.ic_fullscreen else R.drawable.ic_hide),
            contentDescription = "Fullscreen / Minimize",
        )
    }
}

/** Button that toggles the settings screen. */
@Composable
fun SettingButton(show: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (!show) Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
        )
        else Icon(
            painter = painterResource(R.drawable.ic_layout),
            contentDescription = "Main Screen",
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    val startDest = Destination.X11
    val navController = rememberNavController()
    val navBackEntry by navController.currentBackStackEntryAsState()
    val currDestination = appbarDestList.find { navBackEntry?.destination?.hasRoute(it.route::class) == true }
    MainTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                MyTopAppBar(currDestination, { navController.navigate(it.route) })
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                contentAlignment = Alignment.Center,
            ) {
                NavHost(
                    navController, startDest.route,
                    enterTransition = { scaleIn() },
                    exitTransition = { scaleOut() },
                ) {
                    composable<RoutePrepare> { PrepareScreenPreview() }
                    composable<RouteX11> { X11ScreenPreview() }
                    navigation<RouteExceptX11>(startDestination = RouteTerminal) {
                        composable<RouteTerminal> { ProotTerminalScreenPreview() }
//                        composable<NavDest.Terminal> { TerminalScreenPreview() }
                        composable<RouteSettings> { SettingScreenPreview() }
                    }
                }
            }
//            MainDialog(uiState) { mainVM.closeConfirmDialog(it) }
        }
    }
}