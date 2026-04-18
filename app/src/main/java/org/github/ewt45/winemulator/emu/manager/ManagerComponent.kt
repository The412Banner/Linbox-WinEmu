package org.github.ewt45.winemulator.emu.manager

import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.CoroutineScope

abstract class ManagerComponent(
    val scope: CoroutineScope,
    val parent: EmuManager,
) {
    /**
     * Components may depend on each other, so all required services must be ready before this returns.
     */
    abstract suspend fun onCreate()

    /**
     * Avoid coroutines here — the process may exit before a coroutine completes.
     */
    open fun onDestroy() {}

    open fun onResume() {}
    open fun onPause() {}
}



