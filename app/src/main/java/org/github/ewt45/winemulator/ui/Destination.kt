package org.github.ewt45.winemulator.ui

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object RoutePrepare
@Serializable
data object RouteTerminal
@Serializable
data object RouteX11
@Serializable
data object RouteExceptX11
@Serializable
data object RouteSettings

enum class Destination(
    val title: String,
    val route: Any,
    val baseRoute: Any = route,
) {
    Prepare(
        "Setup",
        RoutePrepare,
    ),
    X11(
        "x11",
        RouteX11,
    ),
    ExceptX11(
        "Non-X11",
        RouteExceptX11,
    ),
    Terminal(
        "Terminal",
        RouteTerminal,
    ),

    Settings(
        "Settings",
        RouteSettings,
    )
}

/** Tabs displayed in the app bar. */
val appbarDestList = listOf(Destination.Terminal, Destination.Settings)