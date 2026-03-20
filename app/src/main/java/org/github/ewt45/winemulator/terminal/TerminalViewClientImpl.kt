package org.github.ewt45.winemulator.terminal

import org.github.ewt45.winemulator.MainEmuActivity

class TerminalViewClientImpl(
    val activity: MainEmuActivity,
    val sessionClient: TerminalSessionClientAImpl,
): TerminalViewClientBase() {
}