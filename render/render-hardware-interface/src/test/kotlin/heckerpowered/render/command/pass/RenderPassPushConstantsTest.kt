/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command.pass

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.primitive.pushScreenConstants
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RenderPassPushConstantsTest {
    @Test
    fun scopedWriterSuppliesItsExistingNativeViewBeforeRestoringTheFrame() {
        val recording = Recording()
        val expected = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(0x12345678).array()
        recording.stack.frame {
            val outer = reserve(1, 1)
            recording.pass.pushConstants(setOf(ShaderStage.Vertex), 4, 8) { address ->
                assertEquals(outer + 1, address)
                storeInt(address, 0x12345678)
            }
            assertEquals(outer + 1, reserve(0, 1))
            reserveBuffer(4).putInt(0)
        }
        assertEquals(1, recording.bufferCalls)
        assertEquals(0, recording.addressCalls)
        assertEquals(8, recording.offset)
        assertEquals(setOf(ShaderStage.Vertex), recording.stages)
        assertContentEquals(expected, recording.captured)
    }

    @Test
    fun generatedWriterUsesOneBoundedNativeRecordAndCapturesNamedFields() {
        val recording = Recording()
        val base = recording.stack.frame { reserve(0, 1) }
        recording.pass.pushScreenConstants { color(.25f, .5f, .75f, 1f) }
        val expected = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
            .putFloat(.25f).putFloat(.5f).putFloat(.75f).putFloat(1f).array()
        assertEquals(1, recording.bufferCalls)
        assertEquals(0, recording.addressCalls)
        assertEquals(setOf(ShaderStage.Fragment), recording.stages)
        assertEquals(0, recording.offset)
        assertContentEquals(expected, recording.captured)
        recording.stack.frame {
            assertEquals(base, reserve(0, 1))
            reserveBuffer(64).put(ByteArray(64))
        }
        assertContentEquals(expected, recording.captured)
    }

    @Test
    fun scopedAndGeneratedWritersRestoreTheirFrameAfterConsumerFailure() {
        val recording = Recording()
        val base = recording.stack.frame { reserve(0, 1) }
        val failure = IllegalStateException("Injected push failure")
        recording.failure = failure
        assertSame(failure, assertFailsWith<IllegalStateException> {
            recording.pass.pushConstants(setOf(ShaderStage.Vertex), 4) { storeInt(it, 42) }
        })
        recording.stack.frame { assertEquals(base, reserve(64, 1)) }
        assertSame(failure, assertFailsWith<IllegalStateException> {
            recording.pass.pushScreenConstants { color(1f, 1f, 1f, 1f) }
        })
        recording.stack.frame { assertEquals(base, reserve(64, 1)) }
    }

    @Test
    fun originalAddressOverloadRemainsAvailableToNativeCallers() {
        val recording = Recording()
        recording.stack.frame {
            val address = reserve(4, 1)
            storeInt(address, 42)
            recording.pass.pushConstants(setOf(ShaderStage.Vertex), address, 4, 8)
        }
        assertEquals(0, recording.bufferCalls)
        assertEquals(1, recording.addressCalls)
        assertEquals(8, recording.offset)
        assertEquals(42, ByteBuffer.wrap(recording.captured).order(ByteOrder.nativeOrder()).getInt(0))
    }

    private class Recording {
        val stack = MemoryStack(64)
        var bufferCalls = 0
        var addressCalls = 0
        var offset = -1
        var stages: Set<*> = emptySet<ShaderStage>()
        var captured = byteArrayOf()
        var failure: IllegalStateException? = null

        val pass = Proxy.newProxyInstance(RenderPass::class.java.classLoader, arrayOf(RenderPass::class.java)) { _, method, arguments ->
            when {
                method.name == "getMemoryStack" -> stack
                method.name.startsWith("pushConstants") -> {
                    failure?.let { throw it }
                    stages = arguments[0] as Set<*>
                    val source = arguments[1] as? ByteBuffer
                    if (source != null) {
                        bufferCalls++
                        assertTrue(source.isDirect)
                        assertEquals(source.remaining(), source.capacity())
                        offset = arguments[2] as Int
                        captured = ByteArray(source.remaining())
                        source.duplicate().get(captured)
                    } else {
                        addressCalls++
                        offset = arguments[3] as Int
                        captured = ByteArray(arguments[2] as Int)
                        stack.frame { asByteBuffer(NativeAddress(arguments[1] as Long), captured.size).get(captured) }
                    }
                    null
                }
                else -> error("Unexpected pass operation: ${method.name}")
            }
        } as RenderPass
    }
}
