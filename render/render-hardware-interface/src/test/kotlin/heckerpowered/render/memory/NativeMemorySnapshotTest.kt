/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NativeMemorySnapshotTest {
    @Test
    fun unalignedSubrangeUsesAllocationBaseAndRetainsIndependentBytes() {
        val source = ByteBuffer.allocateDirect(9)
        source.put(byteArrayOf(9, 1, 2, 3, 4, 5, 6, 7, 8))
        source.position(4)
        source.limit(7)
        val snapshot = snapshotNativeBytes(directBufferAddress(source) + 1, 5)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), snapshot)
        assertEquals(4, source.position())
        assertEquals(7, source.limit())
        source.put(1, 99)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), snapshot)
        source.clear()
    }

    @Test
    fun snapshotSurvivesMemoryFrameReuse() {
        val stack = MemoryStack(8)
        val snapshot = stack.frame {
            val address = reserve(8, 1)
            asByteBuffer(address, 8).put(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
            snapshotNativeBytes(address, 8)
        }
        stack.frame { clear(reserve(8, 1), 8) }
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), snapshot)
    }

    @Test
    fun invalidNumericRangesFailBeforeAnyNativeRead() {
        assertFailsWith<IllegalArgumentException> { snapshotNativeBytes(NativeAddress(0), 4) }
        assertFailsWith<IllegalArgumentException> { snapshotNativeBytes(NativeAddress(1), 0) }
        assertFailsWith<IllegalArgumentException> { snapshotNativeBytes(NativeAddress(1), -1) }
        assertFailsWith<IllegalArgumentException> { snapshotNativeBytes(NativeAddress(-2), 2) }
        assertFailsWith<IllegalArgumentException> { snapshotNativeBytes(NativeAddress(-1), Int.MAX_VALUE) }
        assertEquals(NativeAddress(Long.MAX_VALUE), validateNativeByteRange(NativeAddress(Long.MAX_VALUE), 4, 8))
        assertEquals(NativeAddress(Long.MIN_VALUE), validateNativeByteRange(NativeAddress(Long.MIN_VALUE), 4, 8))
        assertEquals(NativeAddress(-5), validateNativeByteRange(NativeAddress(-5), 4, 8))
        assertFailsWith<IllegalArgumentException> { validateNativeByteRange(NativeAddress(-1), 1, 8) }
    }

    @Test
    fun thirtyTwoBitPointersNormalizeZeroAndCorrectSignExtensionWithoutReadingMemory() {
        assertEquals(NativeAddress(0x8000_0000L), validateNativeByteRange(NativeAddress(0x8000_0000L), 4, 4))
        assertEquals(NativeAddress(0x8000_0000L), validateNativeByteRange(NativeAddress(Int.MIN_VALUE.toLong()), 4, 4))
        assertEquals(NativeAddress(0xFFFF_FFFBL), validateNativeByteRange(NativeAddress(-5), 4, 4))
        assertEquals(NativeAddress(0xFFFF_FFFBL), validateNativeByteRange(NativeAddress(0xFFFF_FFFBL), 4, 4))
    }

    @Test
    fun thirtyTwoBitPointersRejectNoncanonicalValuesAndUnrepresentableExclusiveEnds() {
        for (address in listOf(0x1_0000_0001L, -0x1_0000_0000L, -0xFFFF_FFFFL)) {
            assertFailsWith<IllegalArgumentException> { validateNativeByteRange(NativeAddress(address), 4, 4) }
        }
        for (address in listOf(0xFFFF_FFFEL, -2L)) {
            assertFailsWith<IllegalArgumentException> { validateNativeByteRange(NativeAddress(address), 4, 4) }
            assertFailsWith<IllegalArgumentException> { validateNativeByteRange(NativeAddress(address), 2, 4) }
            assertEquals(NativeAddress(0xFFFF_FFFEL), validateNativeByteRange(NativeAddress(address), 1, 4))
        }
        assertFailsWith<IllegalArgumentException> { validateNativeByteRange(NativeAddress(0xFFFF_FFFFL), 1, 4) }
        assertFailsWith<IllegalArgumentException> { validateNativeByteRange(NativeAddress(-1), 1, 4) }
    }

    @Test
    fun unsupportedPointerWidthsFailExplicitlyWithoutReadingMemory() {
        for (pointerBytes in listOf(0, 2, 16)) {
            assertFailsWith<IllegalStateException> { validateNativeByteRange(NativeAddress(1), 1, pointerBytes) }
        }
    }

    @Test
    fun reflectedErrorsRetainTheirOriginalIdentity() {
        val method = ReflectionFailure::class.java.getMethod("fail")
        val failure = assertFailsWith<AssertionError> { invokeMemoryMethod(method, ReflectionFailure) }
        assertSame(ReflectionFailure.failure, failure)
    }

    object ReflectionFailure {
        val failure = AssertionError("native memory reflection test")

        fun fail(): Unit = throw failure
    }
}
