package com.fazz.agent.core.tools

import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExecutorTest {

    private class FakeGateway(var grant: Boolean = true, var approve: Boolean = true) : UserInteractionGateway {
        var permissionAsks = 0
        var confirmAsks = 0
        var onGrant: () -> Unit = {}
        override suspend fun requestPermissions(permissions: List<String>, rationale: String): Boolean {
            permissionAsks++; if (grant) onGrant(); return grant
        }
        override suspend fun confirm(request: ConfirmationRequest): Boolean { confirmAsks++; return approve }
    }

    private class EchoTool(
        override val requiredPermissions: List<String> = emptyList(),
        private val sensitive: Boolean = false,
        private val work: suspend () -> Unit = {},
    ) : AgentTool {
        var executed = false
        override val name = "echo"
        override val description = "echo"
        override val activityLabel = "Echoing"
        override val timeoutMs = 1_000L
        override val parameters = schema { string("text", "t", required = true, maxLength = 10) }
        override fun confirmation(args: JsonObject) = if (sensitive) ConfirmationRequest("t", "m") else null
        override suspend fun execute(args: JsonObject): ToolResult { work(); executed = true; return ToolResult.ok(args.string("text")!!) }
    }

    private fun executor(tool: AgentTool, granted: MutableSet<String> = mutableSetOf(), gw: FakeGateway = FakeGateway()) =
        ToolExecutor(ToolRegistry(listOf(tool)), PermissionChecker { it in granted }, gw)

    @Test fun `runs a valid call`() = runTest {
        val r = executor(EchoTool()).execute("echo", jsonOf("text" to "hi"))
        assertTrue(r.success); assertEquals("hi", r.summary)
    }

    @Test fun `unknown tool is rejected`() = runTest {
        assertEquals(ErrorCodes.UNKNOWN_TOOL, executor(EchoTool()).execute("rm_rf", jsonOf()).errorCode)
    }

    @Test fun `invalid arguments are rejected before execution`() = runTest {
        val tool = EchoTool()
        val ex = executor(tool)
        assertEquals(ErrorCodes.INVALID_ARGUMENTS, ex.execute("echo", jsonOf()).errorCode)
        assertEquals(ErrorCodes.INVALID_ARGUMENTS, ex.execute("echo", jsonOf("text" to 5)).errorCode)
        assertEquals(ErrorCodes.INVALID_ARGUMENTS, ex.execute("echo", jsonOf("text" to "way too long text")).errorCode)
        assertEquals(ErrorCodes.INVALID_ARGUMENTS, ex.execute("echo", jsonOf("text" to "a", "cmd" to "x")).errorCode)
        assertFalse(tool.executed)
    }

    @Test fun `permission denied stops execution`() = runTest {
        val tool = EchoTool(requiredPermissions = listOf("android.permission.READ_CONTACTS"))
        val gw = FakeGateway(grant = false)
        val r = executor(tool, gw = gw).execute("echo", jsonOf("text" to "hi"))
        assertEquals(ErrorCodes.PERMISSION_DENIED, r.errorCode)
        assertEquals(1, gw.permissionAsks)
        assertFalse(tool.executed)
    }

    @Test fun `permission granted continues`() = runTest {
        val perm = "android.permission.READ_CONTACTS"
        val granted = mutableSetOf<String>()
        val gw = FakeGateway(grant = true).apply { onGrant = { granted += perm } }
        val r = executor(EchoTool(listOf(perm)), granted, gw).execute("echo", jsonOf("text" to "hi"))
        assertTrue(r.success)
    }

    @Test fun `already granted permission does not prompt`() = runTest {
        val perm = "p"
        val gw = FakeGateway()
        executor(EchoTool(listOf(perm)), mutableSetOf(perm), gw).execute("echo", jsonOf("text" to "hi"))
        assertEquals(0, gw.permissionAsks)
    }

    @Test fun `sensitive action requires confirmation and respects decline`() = runTest {
        val tool = EchoTool(sensitive = true)
        val gw = FakeGateway(approve = false)
        val r = executor(tool, gw = gw).execute("echo", jsonOf("text" to "hi"))
        assertEquals(ErrorCodes.USER_DECLINED, r.errorCode)
        assertEquals(1, gw.confirmAsks)
        assertFalse(tool.executed)
    }

    @Test fun `sensitive action runs after approval`() = runTest {
        val tool = EchoTool(sensitive = true)
        assertTrue(executor(tool, gw = FakeGateway(approve = true)).execute("echo", jsonOf("text" to "hi")).success)
        assertTrue(tool.executed)
    }

    @Test fun `slow tool times out`() = runTest {
        val r = executor(EchoTool(work = { delay(5_000) })).execute("echo", jsonOf("text" to "hi"))
        assertEquals(ErrorCodes.TIMEOUT, r.errorCode)
    }

    @Test fun `exceptions become structured errors`() = runTest {
        val r = executor(EchoTool(work = { error("kaboom") })).execute("echo", jsonOf("text" to "hi"))
        assertEquals(ErrorCodes.EXECUTION_FAILED, r.errorCode)
        assertTrue(r.summary.contains("kaboom"))
    }

    @Test fun `security exception maps to permission denied`() = runTest {
        val r = executor(EchoTool(work = { throw SecurityException("nope") })).execute("echo", jsonOf("text" to "hi"))
        assertEquals(ErrorCodes.PERMISSION_DENIED, r.errorCode)
    }
}
