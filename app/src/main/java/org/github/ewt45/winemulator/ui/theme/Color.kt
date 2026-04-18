package org.github.ewt45.winemulator.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== Original colors ====================
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ==================== Terminal colors ====================
// Terminal dark theme
val TerminalBackground = Color(0xFF000000)           // pure black background
val TerminalSurface = Color(0xFF121212)              // surface color
val TerminalSurfaceVariant = Color(0xFF1E1E1E)       // surface variant
val TerminalOnBackground = Color(0xFFFFFFFF)        // text on background
val TerminalOnSurface = Color(0xFFE0E0E0)            // text on surface

// Prompt colors
val TerminalUserGreen = Color(0xFF81C784)            // username green (Unix-style prompt)
val TerminalUserBlue = Color(0xFF64B5F6)             // normal user blue
val TerminalHostCyan = Color(0xFF4DD0E1)             // hostname cyan
val TerminalPathWhite = Color(0xFFFFFFFF)            // path white
val TerminalSymbolYellow = Color(0xFFFFD54F)         // symbol yellow (@ : $ #)
val TerminalRootWhite = Color(0xFFE0E0E0)            // root user white

// Status indicator colors
val TerminalOnlineGreen = Color(0xFF4CAF50)          // online status green
val TerminalOfflineRed = Color(0xFFF44336)           // offline status red

// Terminal cursor colors
val TerminalCursor = Color(0xFFFFFFFF)               // cursor white
val TerminalCursorBlock = Color(0xFFFFFFFF).copy(alpha = 0.7f)  // block cursor

// Errors and warnings
val TerminalError = Color(0xFFEF5350)               // error red
val TerminalWarning = Color(0xFFFFCA28)              // warning yellow
val TerminalInfo = Color(0xFF29B6F6)                 // info blue

// ==================== Keyboard colors ====================
// Keyboard light theme
val KeyboardBackground = Color(0xFFF5F5F5)           // keyboard background light gray
val KeyboardSurface = Color(0xFFFFFFFF)             // key background white
val KeyboardSurfaceVariant = Color(0xFFE8E8E8)       // key variant gray
val KeyboardOnBackground = Color(0xFF333333)         // text on background dark gray
val KeyboardOnSurface = Color(0xFF1A1A1A)            // text on key

// Function key colors
val KeyboardFunctionKey = Color(0xFFE0E0E0)          // function key background
val KeyboardFunctionKeyText = Color(0xFF666666)     // function key text

// Key style — skeuomorphic
val KeyboardKeyShadow = Color(0x40000000)           // key shadow (25% black)
val KeyboardKeyHighlight = Color(0x20FFFFFF)         // key highlight (12% white)
val KeyboardKeyPressed = Color(0xFFD0D0D0)           // pressed state background

// Key borders
val KeyboardKeyBorder = Color(0xFFD0D0D0)           // key border light gray
val KeyboardKeyBorderDark = Color(0xFFB0B0B0)       // key border dark gray

// ==================== Navigation bar colors ====================
// Tab indicator
val TabIndicatorPurple = Color(0xFF9C27B0)           // tab indicator purple
val TabIndicatorLight = Color(0xFFE1BEE7)           // tab indicator light purple

// Navigation bar background
val NavBarBackground = Color(0xFF1E1E1E)             // dark nav bar
val NavBarBackgroundLight = Color(0xFFFAFAFA)       // light nav bar

// ==================== Utility colors ====================
// Opacity variants
val OverlayLight = Color(0x0AFFFFFF)                 // 10% white
val OverlayDark = Color(0x1A000000)                  // 10% black

// Dividers
val DividerDark = Color(0xFF2A2A2A)                  // dark divider
val DividerLight = Color(0xFFE0E0E0)                 // light divider

// Selection state
val SelectionHighlight = Color(0x409C27B0)          // selection highlight purple (25%)
