package com.fazz.agent

import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.InteractionBroker
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractionBrokerTest {
    private val req = ConfirmationRequest(title = "Kirim?", message = "Tes", confirmLabel = "Izinkan", denyLabel = "Tolak")

    @Test fun unansweredApprovalIsDeniedAfterTimeout() = runBlocking {
        val broker = InteractionBroker(timeoutMs = 50)
        assertEquals(false, broker.confirm(req))
        assertNull(broker.pending.value)
    }

    @Test fun answeredApprovalReturnsAnswer() = runBlocking {
        val broker = InteractionBroker(timeoutMs = 5_000)
        val answer = async { broker.confirm(req) }
        while (broker.pending.value == null) delay(5)
        broker.resolve(true)
        assertEquals(true, answer.await())
    }
}
