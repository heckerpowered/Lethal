/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.engine.prepare.BufferUpload
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.resource.buffer.BufferUsage
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView
import sun.misc.Unsafe
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import kotlin.test.*

class UploadDataTest {
    @Test
    fun bufferRecordingUsesTheEncoderStackAndCapturesBeforeItIsReused() {
        val stack = MemoryStack(8)
        val expected = byteArrayOf(1, 2, 3, 4)
        val buffer = Proxy.newProxyInstance(GpuBuffer::class.java.classLoader, arrayOf(GpuBuffer::class.java)) { _, method, _ ->
            when (method.name) {
                "getSizeBytes" -> expected.size.toLong()
                "getUsage" -> setOf(BufferUsage.TransferDestination)
                else -> error("Unexpected buffer access: ${method.name}")
            }
        } as GpuBuffer
        val destination = GpuBufferView(buffer, 0, expected.size.toLong())
        val base = stack.frame { reserve(0, 1) }
        var captured = byteArrayOf()
        val encoder = Proxy.newProxyInstance(CommandEncoder::class.java.classLoader, arrayOf(CommandEncoder::class.java)) { _, method, arguments ->
            when {
                method.name == "getMemoryStack" -> stack
                method.name.startsWith("writeBuffer") -> {
                    assertSame(destination, arguments[0])
                    assertEquals(base.rawValue, arguments[1])
                    captured = readBytes(base, expected.size)
                    null
                }

                else -> error("Unexpected encoder access: ${method.name}")
            }
        } as CommandEncoder
        BufferUpload(destination, UploadData(expected)).encode(encoder)
        stack.frame { reserveBuffer(8).put(ByteArray(8) { 99 }) }
        assertContentEquals(expected, captured)
    }

    @Test
    fun repeatedSmallUploadsReuseNativeStorageAndCaptureIndependentBytes() {
        val stack = MemoryStack(16)
        val expected = byteArrayOf(1, 2, 3, 4)
        val upload = UploadData(expected)
        val base = stack.frame { reserve(0, 1) }
        val captured = ArrayList<ByteArray>()
        val directBuffers = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java).single { it.name == "direct" }
        val countBefore = directBuffers.count
        repeat(64) {
            upload.consumeNative(stack) { address ->
                assertEquals(base, address)
                captured.add(readBytes(address, expected.size))
            }
        }
        assertEquals(countBefore, directBuffers.count)
        stack.frame { reserveBuffer(16).put(ByteArray(16) { 99 }) }
        captured.forEach { assertContentEquals(expected, it) }
    }

    @Test
    fun nestedUploadsKeepOuterBytesAndReleaseOnlyTheirOwnReservation() {
        val stack = MemoryStack(16)
        val outer = byteArrayOf(1, 2, 3, 4)
        val inner = byteArrayOf(5, 6, 7, 8)
        UploadData(outer).consumeNative(stack) { outerAddress ->
            UploadData(inner).consumeNative(stack) { innerAddress ->
                assertEquals(outerAddress + outer.size, innerAddress)
                assertContentEquals(outer, readBytes(outerAddress, outer.size))
                assertContentEquals(inner, readBytes(innerAddress, inner.size))
            }
            stack.frame { assertEquals(outerAddress + outer.size, reserve(0, 1)) }
            assertContentEquals(outer, readBytes(outerAddress, outer.size))
        }
    }

    @Test
    fun callbackFailureRestoresTheSuppliedFrame() {
        val stack = MemoryStack(8)
        val base = stack.frame { reserve(0, 1) }
        val failure = IllegalStateException("Injected consumer failure")
        val observed = assertFailsWith<IllegalStateException> {
            UploadData(byteArrayOf(1, 2, 3, 4)).consumeNative(stack) { throw failure }
        }
        assertSame(failure, observed)
        stack.frame { assertEquals(base, reserve(8, 1)) }
    }

    @Test
    fun exhaustedOuterFrameUsesIndependentStagingWithoutOverwritingOuterBytes() {
        val stack = MemoryStack(8)
        val expected = byteArrayOf(9, 8, 7, 6)
        stack.frame {
            val outerAddress = reserve(6, 1)
            asByteBuffer(outerAddress, 6).put(ByteArray(6) { 42 })
            val previous = reserve(0, 1)
            UploadData(expected).consumeNative(stack) { address ->
                assertTrue(address.rawValue < outerAddress.rawValue || address.rawValue >= outerAddress.rawValue + 8)
                assertContentEquals(expected, readBytes(address, expected.size))
            }
            assertEquals(previous, reserve(0, 1))
            assertContentEquals(ByteArray(6) { 42 }, readBytes(outerAddress, 6))
            assertFailsWith<IllegalArgumentException> {
                UploadData(expected).consumeNative(stack) { throw IllegalArgumentException("Injected fallback failure") }
            }
            assertEquals(previous, reserve(0, 1))
        }
    }

    @Test
    fun nestedFallbackFailureKeepsOuterStagingAndTheExhaustedFrameIntact() {
        val stack = MemoryStack(8)
        val outer = byteArrayOf(1, 2, 3, 4, 5, 6)
        val inner = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1)
        val failure = IllegalStateException("Injected nested fallback failure")
        stack.frame {
            val occupiedAddress = reserve(8, 1)
            asByteBuffer(occupiedAddress, 8).put(ByteArray(8) { 42 })
            val previous = reserve(0, 1)
            UploadData(outer).consumeNative(stack) { outerAddress ->
                val observed = assertFailsWith<IllegalStateException> {
                    UploadData(inner).consumeNative(stack) { innerAddress ->
                        assertTrue(innerAddress != outerAddress)
                        assertContentEquals(inner, readBytes(innerAddress, inner.size))
                        assertContentEquals(outer, readBytes(outerAddress, outer.size))
                        throw failure
                    }
                }
                assertSame(failure, observed)
                assertContentEquals(outer, readBytes(outerAddress, outer.size))
                assertEquals(previous, reserve(0, 1))
            }
            assertEquals(previous, reserve(0, 1))
            assertContentEquals(ByteArray(8) { 42 }, readBytes(occupiedAddress, 8))
        }
    }

    @Test
    fun uploadsLargerThanDefaultCapacityKeepTheHostSnapshot() {
        val stack = MemoryStack()
        val bytes = ByteArray(70_000) { it.toByte() }
        val expected = bytes.copyOf()
        val upload = UploadData(bytes)
        bytes.fill(0)
        val base = stack.frame { reserve(0, 1) }
        var captured = byteArrayOf()
        upload.consumeNative(stack) { address ->
            assertTrue(address.rawValue < base.rawValue || address.rawValue >= base.rawValue + 64 * 1024)
            captured = readBytes(address, expected.size)
        }
        assertContentEquals(expected, captured)
        stack.frame { assertEquals(base, reserve(64 * 1024, 1)) }
        assertContentEquals(expected, captured)
    }

    @Test
    fun emptySnapshotsFailBeforeInvokingTheConsumerOrReservingStorage() {
        val stack = MemoryStack(8)
        val base = stack.frame { reserve(0, 1) }
        var consumed = false
        assertFailsWith<IllegalArgumentException> { UploadData(byteArrayOf()).consumeNative(stack) { consumed = true } }
        assertFalse(consumed)
        stack.frame { assertEquals(base, reserve(8, 1)) }
    }

    companion object {
        private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe

        private fun readBytes(address: NativeAddress, size: Int): ByteArray =
            ByteArray(size) { unsafe.getByte(address.rawValue + it) }
    }
}
