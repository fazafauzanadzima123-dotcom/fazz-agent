package com.fazz.agent.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether Fazz's own UI is on screen. Approvals are shown in-app when true, in the overlay when false. */
object AppVisibility {
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    fun set(visible: Boolean) { _foreground.value = visible }
}
