package org.github.ewt45.winemulator.terminal

import com.termux.shared.settings.properties.TermuxPropertyConstants
import com.termux.terminal.TerminalEmulator
import org.github.ewt45.winemulator.MainEmuActivity

/**
 * Session client for the main activity.
 */
class SessionClientAImpl(
    val activity: MainEmuActivity,
): SessionClientBase() {

}