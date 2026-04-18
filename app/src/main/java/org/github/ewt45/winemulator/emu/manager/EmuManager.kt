package org.github.ewt45.winemulator.emu.manager

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.github.ewt45.winemulator.MainEmuActivity
import org.github.ewt45.winemulator.emu.Pulseaudio
// TODO account for the case where the user switches an active component
/**
 * Sub-components are started sequentially in a coroutine. Each must guarantee that its required
 * services are ready before returning; long-running work should be launched in a new coroutine.
 */
class EmuManager(private val scope: CoroutineScope) : DefaultLifecycleObserver {
    private val TAG = "EmuManager"
    val sound: SoundManager = SoundManager(scope, this)
    val display: DisplayManager = DisplayManager(scope, this)

    override fun onCreate(owner: LifecycleOwner) {
        scope.launch {
            display.onCreate()
            sound.onCreate()
            // TODO start terminal first, then display and audio, then run the initial command
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        display.onDestroy()
        sound.onDestroy()
    }

    override fun onResume(owner: LifecycleOwner) {
        display.onResume()
        sound.onResume()

    }

    override fun onPause(owner: LifecycleOwner) {
        display.onPause()
        sound.onPause()
    }




}
