/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.render.opengl.RenderbufferName
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import heckerpowered.render.resource.texture.TextureFormat
import java.lang.reflect.Proxy
import kotlin.test.*

class HostDepthFormatTest {
    @Test
    fun importingUsesTheActualSizedDepthFormatAndRestoresThePreviousBinding() {
        for ((native, expected) in listOf(
            0x81A6 to TextureFormat.Depth24UnsignedNormalized,
            0x81A7 to TextureFormat.Depth32UnsignedNormalized,
            0x88F0 to TextureFormat.Depth24UnsignedNormalizedStencil8,
        )) {
            val calls = DepthQuery(native)
            assertEquals(expected, readHostDepthFormat(calls.functions, RenderbufferName(7)))
            assertEquals(listOf(7, 31), calls.bindings)
            assertEquals(31, calls.bound)
        }
    }

    @Test
    fun unsupportedStorageReportsItsActualFormatAndRestoresTheHostBinding() {
        val calls = DepthQuery(0x8CAC)
        val failure = assertFailsWith<IllegalStateException> { readHostDepthFormat(calls.functions, RenderbufferName(7)) }
        assertTrue(requireNotNull(failure.message).contains("0x8cac"))
        assertEquals(31, calls.bound)
    }

    @Test
    fun failedNativeQueryStillRestoresTheHostBinding() {
        val calls = DepthQuery(0x81A7, queryFails = true)
        assertFailsWith<IllegalStateException> { readHostDepthFormat(calls.functions, RenderbufferName(7)) }
        assertEquals(31, calls.bound)
    }

    @Test
    fun absentNativeStorageIsRejectedBeforeChangingAnyBinding() {
        val calls = DepthQuery(0x81A7, alive = false)
        assertFailsWith<IllegalArgumentException> { readHostDepthFormat(calls.functions, RenderbufferName(7)) }
        assertTrue(calls.bindings.isEmpty())
    }
}

private class DepthQuery(private val format: Int, private val alive: Boolean = true, private val queryFails: Boolean = false) {
    var bound = 31
    val bindings = mutableListOf<Int>()
    val functions = Proxy.newProxyInstance(OpenGLFramebufferFunctions::class.java.classLoader, arrayOf(OpenGLFramebufferFunctions::class.java)) { _, method, arguments ->
        when (method.name.substringBefore('-')) {
            "isRenderbuffer" -> alive
            "getBoundRenderbuffer" -> bound
            "bindRenderbuffer" -> { bound = arguments[0] as Int; bindings += bound; Unit }
            "getRenderbufferParameter" -> {
                check(bound == 7 && arguments[0] == 0x8D44)
                check(!queryFails) { "Native query failed" }
                format
            }
            else -> error(method.name)
        }
    } as OpenGLFramebufferFunctions
}
