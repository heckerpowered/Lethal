/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import sun.misc.Unsafe
import java.nio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MemoryFrameTest {
    @Test
    fun repeatedFramesReuseOneNativeAllocationForAddressesAndBufferViews() {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val addressOffset = unsafe.objectFieldOffset(Buffer::class.java.getDeclaredField("address"))
        var allocations = 0
        val stack = MemoryStack(16) { buffer ->
            allocations++
            NativeAddress(unsafe.getLong(buffer, addressOffset))
        }
        val base = stack.frame { reserve(0, 1) }
        repeat(64) {
            stack.frame {
                val address = checkNotNull(tryReserve(4, 1))
                assertEquals(base, address)
                asByteBuffer(address, 4).putInt(42)
                assertEquals(42, loadInt(address))
                val view = checkNotNull(tryReserveBuffer(4))
                view.putInt(73)
                assertEquals(73, loadInt(address + 4))
            }
        }
        assertEquals(1, allocations)
    }

    @Test
    fun capacityFailuresAndInvalidArgumentsDoNotAdvanceThePointer() {
        val stack = MemoryStack(8)
        stack.frame {
            val base = reserve(0, 1)
            assertNull(tryReserve(Int.MAX_VALUE, 1))
            assertNull(tryReserveBuffer(9))
            assertFailsWith<IllegalStateException> { reserve(9, 1) }
            assertFailsWith<IllegalArgumentException> { tryReserve(-1, 1) }
            listOf(0, -1, 3).forEach { alignment ->
                assertFailsWith<IllegalArgumentException> { tryReserve(1, alignment) }
                assertFailsWith<IllegalArgumentException> { tryReserveBuffer(1, alignment) }
            }
            assertEquals(base, reserve(8, 1))
            assertNull(tryReserve(1, 1))
            assertEquals(base + 8, tryReserve(0, 1))
        }
    }

    @Test
    fun alignmentPaddingIncludingEmptyReservationsMustFit() {
        val stack = MemoryStack(8) { NativeAddress(0x1001) }
        stack.frame {
            assertNull(tryReserve(0, 16))
            assertEquals(NativeAddress(0x1001), reserve(0, 1))
            assertEquals(NativeAddress(0x1004), tryReserve(2, 4))
            assertEquals(NativeAddress(0x1006), reserve(0, 1))
        }
    }

    @Test
    fun addressAlignmentOverflowCannotBecomeAReservation() {
        val stack = MemoryStack(8) { NativeAddress(Long.MAX_VALUE - 15) }
        stack.frame {
            reserve(1, 1)
            val previous = reserve(0, 1)
            assertNull(tryReserve(0, 16))
            assertEquals(previous, reserve(0, 1))
        }
    }
}
