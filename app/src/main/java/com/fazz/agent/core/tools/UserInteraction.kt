package com.fazz.agent.core.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** How tools reach the human: runtime permission prompts and explicit confirmations. */
interface UserInteractionGateway {
    suspend fun requestPermissions(permissions: List<String>, rationale: String): Boolean
    suspend fun confirm(request: ConfirmationRequest): Boolean
}

/** Checks whether Android runtime permissions are already granted. */
fun interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}

sealed interface PendingInteraction {
    data class Permission(val permissions: List<String>, val rationale: String, val result: CompletableDeferred<Boolean>) : PendingInteraction
    data class Confirmation(val request: ConfirmationRequest, val result: CompletableDeferred<Boolean>) : PendingInteraction
}

/**
 * UI-backed gateway: the agent suspends until the user answers a dialog shown by the chat screen.
 * Requests are serialized so only one dialog is visible at a time.
 */
class InteractionBroker(private val timeoutMs: Long = 3 * 60_000L) : UserInteractionGateway {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<PendingInteraction?>(null)
    val pending: StateFlow<PendingInteraction?> = _pending.asStateFlow()

    override suspend fun requestPermissions(permissions: List<String>, rationale: String): Boolean = mutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        _pending.value = PendingInteraction.Permission(permissions, rationale, deferred)
        try { withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false } finally { _pending.value = null }
    }

    override suspend fun confirm(request: ConfirmationRequest): Boolean = mutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        _pending.value = PendingInteraction.Confirmation(request, deferred)
        // Never wait forever: an unanswered approval counts as "Tolak" so the agent can't hang.
        try { withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false } finally { _pending.value = null }
    }

    /** Called by the UI. */
    fun resolve(answer: Boolean) {
        when (val p = _pending.value) {
            is PendingInteraction.Permission -> p.result.complete(answer)
            is PendingInteraction.Confirmation -> p.result.complete(answer)
            null -> Unit
        }
    }
}
