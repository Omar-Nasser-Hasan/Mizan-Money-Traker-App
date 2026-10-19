// Copyright (c) 2026 Mizan contributors
package app.mizan.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LockGateState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val locked: Boolean = true,
    val foreground: Boolean = true,
    val timeoutSeconds: Int = 30,
) {
    val blocksShell: Boolean get() = !loaded || (enabled && locked)
}

/** Authentication is process-local and deliberately never restored from saved state. */
class LockGate(
    private val elapsedMillis: () -> Long,
) {
    private val mutableState = MutableStateFlow(LockGateState())
    val state = mutableState.asStateFlow()
    private var backgroundedAt: Long? = null

    fun configure(
        enabled: Boolean,
        timeoutSeconds: Int,
    ) {
        require(timeoutSeconds >= 0)
        val current = state.value
        mutableState.value =
            current.copy(
                loaded = true,
                enabled = enabled,
                timeoutSeconds = timeoutSeconds,
                locked = enabled && (!current.loaded || !current.enabled || current.locked),
            )
    }

    fun onBackground() {
        if (state.value.foreground) backgroundedAt = elapsedMillis()
        mutableState.value = state.value.copy(foreground = false)
    }

    fun onForeground() {
        val current = state.value
        val timedOut = backgroundedAt?.let { elapsedMillis() - it >= current.timeoutSeconds.toLong() * 1_000 } ?: false
        mutableState.value = current.copy(foreground = true, locked = current.locked || (current.enabled && timedOut))
        backgroundedAt = null
    }

    fun authenticated() {
        if (!state.value.loaded) return
        backgroundedAt = null
        mutableState.value = state.value.copy(locked = false)
    }
}
