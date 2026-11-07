package com.fazz.agent.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether any Fazz screen is visible. Counted (not a single boolean) because Android may briefly keep two
 * activity instances, and one stopping must not hide the other. Approvals are shown in-app when visible,
 * otherwise in the floating overlay and as a notification.
 */
object AppVisibility {
    private val started = AtomicInteger(0)
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    fun onStart() { _foreground.value = started.incrementAndGet() > 0 }
    fun onStop() { _foreground.value = started.updateAndGet { (it - 1).coerceAtLeast(0) } > 0 }

    /** Set when the user leaves Fazz to enable screen control, so Fazz can return automatically. */
    @Volatile var returnAfterAccessibilitySetup: Boolean = false
}
