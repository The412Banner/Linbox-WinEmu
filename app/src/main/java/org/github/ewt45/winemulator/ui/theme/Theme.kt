package org.github.ewt45.winemulator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Additional dark color scheme (used for floating buttons etc.)
private val DarkSurfaceColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = androidx.compose.ui.graphics.Color(0xFF121212),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    background = androidx.compose.ui.graphics.Color(0xFF121212)
)

@Composable
fun MainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    // Flow for externally injected theme preference (true = dark, false = light, null = follow system)
    externalDarkThemeFlow: Flow<Boolean?>? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Collect externally injected theme preference
    val externalDarkTheme by externalDarkThemeFlow?.collectAsState(initial = null) ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }
    
    // Resolve final theme: external preference takes priority, then darkTheme parameter (defaults to dark)
    val isDarkTheme = externalDarkTheme ?: darkTheme ?: true
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}