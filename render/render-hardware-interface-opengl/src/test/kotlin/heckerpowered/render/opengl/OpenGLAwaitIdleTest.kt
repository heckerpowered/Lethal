/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.opengl.function.OpenGLFunctions
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OpenGLAwaitIdleTest {
    @Test
    fun explicitWaitUsesNativeCompletionWithErrorChecksAndDoesNotFlush() {
        val driver = IdleDriver()
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            driver.calls.clear()
            device.awaitIdle()
            assertEquals(listOf("context", "getError", "finish", "getError"), driver.calls)
        }
    }

    @Test
    fun pendingNativeErrorRejectsBeforeStartingCompletionWait() {
        val driver = IdleDriver()
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            driver.calls.clear()
            driver.nativeError = 0x0502
            val failure = assertFailsWith<OpenGLOperationException> { device.awaitIdle() }
            assertEquals(0x0502, failure.errorCode)
            assertEquals(listOf("context", "getError"), driver.calls)
        }
    }

    @Test
    fun nativeWaitErrorIsReportedAndOnlySuccessfulRetryReturns() {
        val driver = IdleDriver()
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            driver.calls.clear()
            driver.finishError = 0x0502
            assertFailsWith<OpenGLOperationException> { device.awaitIdle() }
            assertEquals(listOf("context", "getError", "finish", "getError"), driver.calls)
            driver.finishError = 0
            device.awaitIdle()
            assertEquals(2, driver.calls.count { it == "finish" })
        }
    }

    @Test
    fun nativeWaitThrowablePropagatesWithoutClaimingCompletion() {
        val driver = IdleDriver()
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            driver.calls.clear()
            val failure = AssertionError("native completion failed")
            driver.finishFailure = failure
            assertSame(failure, assertFailsWith<AssertionError> { device.awaitIdle() })
            assertEquals(listOf("context", "getError", "finish"), driver.calls)
        }
    }

    @Test
    fun encodingCallbackCannotWaitAndWaitBecomesValidAfterExit() {
        val driver = IdleDriver()
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            driver.calls.clear()
            device.encode("wait scope") {
                assertFailsWith<IllegalStateException> { device.awaitIdle() }
            }
            assertTrue(driver.calls.none { it == "finish" })
            device.awaitIdle()
            assertEquals(1, driver.calls.count { it == "finish" })
        }
    }

    @Test
    fun exceptionalEncodingExitStillAllowsExplicitCompletionWithoutImplicitFlush() {
        val driver = IdleDriver()
        OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler()).use { device ->
            driver.calls.clear()
            val failure = IllegalArgumentException("recording abandoned")
            assertSame(failure, assertFailsWith<IllegalArgumentException> {
                device.encode("failed recording") { throw failure }
            })
            assertTrue(driver.calls.none { it == "flush" || it == "finish" })
            device.awaitIdle()
            assertEquals(1, driver.calls.count { it == "finish" })
        }
    }

    @Test
    fun wrongContextThreadAndClosedDeviceRejectBeforeNativeWait() {
        val driver = IdleDriver()
        val device = OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler())
        driver.calls.clear()
        driver.current = false
        assertFailsWith<IllegalStateException> { device.awaitIdle() }
        driver.current = true
        val failure = AtomicReference<Throwable>()
        Thread {
            try {
                device.awaitIdle()
            } catch (caught: IllegalStateException) {
                failure.set(caught)
            }
        }.apply { start(); join() }
        assertTrue(failure.get() is IllegalStateException)
        assertTrue(driver.calls.none { it == "finish" })
        device.close()
        driver.calls.clear()
        assertFailsWith<IllegalStateException> { device.awaitIdle() }
        assertTrue(driver.calls.isEmpty())
    }

    @Test
    fun closeDoesNotPerformCompletionOrFlushEvenAfterExplicitWait() {
        val driver = IdleDriver()
        val device = OpenGLGraphicsDevice(driver.functions, canonicalShaderCompiler = RecordingCompiler())
        device.awaitIdle()
        driver.calls.clear()
        device.close()
        device.close()
        assertTrue(driver.calls.none { it == "finish" || it == "flush" })
    }
}

private class IdleDriver {
    private val thread = Thread.currentThread()
    var current = true
    var nativeError = 0
    var finishError = 0
    var finishFailure: AssertionError? = null
    val calls = mutableListOf<String>()
    private val framebuffers = Proxy.newProxyInstance(OpenGLFramebufferFunctions::class.java.classLoader, arrayOf(OpenGLFramebufferFunctions::class.java)) { _, method, _ ->
        error("Unexpected framebuffer operation ${method.name}")
    } as OpenGLFramebufferFunctions
    val functions = Proxy.newProxyInstance(OpenGLFunctions::class.java.classLoader, arrayOf(OpenGLFunctions::class.java)) { _, method, _ ->
        when (method.name) {
            "checkCurrentContext" -> { calls.add("context"); check(current && Thread.currentThread() === thread); null }
            "getError" -> { calls.add("getError"); nativeError.also { nativeError = 0 } }
            "getFramebuffers" -> framebuffers
            "finish" -> {
                calls.add("finish")
                finishFailure?.let { throw it }
                nativeError = finishError
                null
            }
            "flush" -> { calls.add("flush"); null }
            else -> error("Unexpected native operation ${method.name}")
        }
    } as OpenGLFunctions
}
