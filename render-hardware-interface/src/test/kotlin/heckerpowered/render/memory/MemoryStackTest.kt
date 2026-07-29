/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoryStackTest {
    @Test
    fun nestedFramesReclaimOnlyTheirOwnAllocations() {
        val stack = memoryStack()
        stack.alloc(ints(1)) { outerAddress ->
            storeInt(outerAddress, 11)

            val nestedAddress = stack.alloc(ints(1)) { address ->
                storeInt(address, 22)
                address
            }

            val reusedAddress = alloc(ints(1)) { address ->
                storeInt(address, 33)
                address
            }

            assertEquals(expected = nestedAddress, actual = reusedAddress)
            assertEquals(expected = 11, actual = loadInt(outerAddress))
            assertEquals(expected = 33, actual = loadInt(nestedAddress))
        }
    }

    @Test
    fun packedAllocationAlignsEachLayoutWithoutIntermediateObjects() {
        val stack = memoryStack(baseAddress = 1L)
        stack.alloc(bytes(1), floats(1), bytes(1), ints(1)) { byteAddress, floatAddress, secondByteAddress, intAddress ->
            assertEquals(expected = NativeAddress(1L), actual = byteAddress)
            assertEquals(expected = NativeAddress(4L), actual = floatAddress)
            assertEquals(expected = NativeAddress(8L), actual = secondByteAddress)
            assertEquals(expected = NativeAddress(12L), actual = intAddress)
        }
    }

    @Test
    fun allocationBeyondFixedCapacityFailsWithoutMovingThePointer() {
        val stack = memoryStack(capacity = 16)
        val exception = assertFailsWith<IllegalStateException> { stack.alloc(bytes(17)) {} }

        assertEquals(expected = "Memory stack overflow: requested=17 bytes, remaining=16 bytes", actual = exception.message)
        stack.alloc(ints(1)) { address -> assertEquals(expected = NativeAddress(BASE_ADDRESS), actual = address) }
    }

    @Test
    fun failedNestedFrameRestoresItsAllocationPosition() {
        val stack = memoryStack()
        stack.alloc(ints(1)) { outerAddress ->
            storeInt(outerAddress, 11)
            var failedFrameAddress = NativeAddress(0L)

            assertFailsWith<ExpectedFailure> {
                stack.alloc(ints(1)) { address ->
                    failedFrameAddress = address
                    throw ExpectedFailure()
                }
            }

            val reusedAddress = alloc(ints(1)) { address ->
                storeInt(address, 22)
                address
            }
            assertEquals(expected = failedFrameAddress, actual = reusedAddress)
            assertEquals(expected = 11, actual = loadInt(outerAddress))
            assertEquals(expected = 22, actual = loadInt(failedFrameAddress))
        }
    }

    @Test
    fun memoryLayoutRetainsSizeAndAlignment() {
        val layout = MemoryLayout.of(48, 16)

        assertEquals(expected = 48, actual = layout.byteCount)
        assertEquals(expected = 16, actual = layout.alignment)
    }

    @Test
    fun memoryLayoutRejectsInvalidAlignment() {
        assertFailsWith<IllegalArgumentException> { MemoryLayout.of(4, 3) }
    }

    @Test
    fun contiguousFloatLoadCopiesTheCompleteRegion() {
        val expected = floatArrayOf(1.0F, 2.0F, 3.0F, 4.0F)
        val actual = FloatArray(expected.size)

        memoryStack().alloc(floats(expected.size)) { address ->
            storeFloats(address, expected)
            loadFloats(address, actual)
        }

        assertContentEquals(expected = expected, actual = actual)
    }

    @Test
    fun float4StoreWritesEveryComponent() {
        val actual = FloatArray(4)

        memoryStack().alloc(floats(4)) { address ->
            storeFloat4(address, 1.0F, 2.0F, 3.0F, 4.0F)
            loadFloats(address, actual)
        }

        assertContentEquals(expected = floatArrayOf(1.0F, 2.0F, 3.0F, 4.0F), actual = actual)
    }

    @Test
    fun floatVectorStoresWriteEveryComponent() {
        val actual = FloatArray(5)

        memoryStack().alloc(floats(5)) { address ->
            storeFloat2(address, 1.0F, 2.0F)
            storeFloat3(address + Float.SIZE_BYTES * 2, 3.0F, 4.0F, 5.0F)
            loadFloats(address, actual)
        }

        assertContentEquals(expected = floatArrayOf(1.0F, 2.0F, 3.0F, 4.0F, 5.0F), actual = actual)
    }

    @Test
    fun clearZeroesTheCompleteRange() {
        val actual = FloatArray(4)

        memoryStack().alloc(floats(4)) { address ->
            storeFloat4(address, 1.0F, 2.0F, 3.0F, 4.0F)
            clear(address + Float.SIZE_BYTES, Float.SIZE_BYTES * 2)
            loadFloats(address, actual)
        }

        assertContentEquals(expected = floatArrayOf(1.0F, 0.0F, 0.0F, 4.0F), actual = actual)
    }

    @Test
    fun nativeRangeComparisonDoesNotRequireAnIntermediateCopy() {
        val stack = memoryStack()
        val matching = ByteBuffer.allocate(Int.SIZE_BYTES * 2).order(ByteOrder.nativeOrder()).putInt(11).putInt(22).array()
        val different = ByteBuffer.allocate(Int.SIZE_BYTES * 2).order(ByteOrder.nativeOrder()).putInt(11).putInt(23).array()
        stack.alloc(ints(2)) { address ->
            storeInt(address, 11)
            storeInt(address + Int.SIZE_BYTES, 22)

            assertEquals(expected = true, actual = stack.contentEquals(address, matching, 0, matching.size))
            assertEquals(expected = false, actual = stack.contentEquals(address, different, 0, different.size))
        }
    }

    @Test
    fun generatedSixteenElementOverloadPacksEveryAddress() {
        val stack = memoryStack()
        stack.alloc(
            bytes(1), bytes(1), bytes(1), bytes(1),
            bytes(1), bytes(1), bytes(1), bytes(1),
            bytes(1), bytes(1), bytes(1), bytes(1),
            bytes(1), bytes(1), bytes(1), bytes(1),
        ) { first, second, third, fourth, fifth, sixth, seventh, eighth, ninth, tenth, eleventh, twelfth, thirteenth, fourteenth, fifteenth, sixteenth ->
            val addresses = listOf(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth, tenth, eleventh, twelfth, thirteenth, fourteenth, fifteenth, sixteenth)
            assertEquals(expected = (0..15).map { NativeAddress(BASE_ADDRESS + it) }, actual = addresses)
        }
    }

    private fun memoryStack(capacity: Int = 64 * 1024, baseAddress: Long = BASE_ADDRESS): MemoryStack {
        return MemoryStack(capacity) { NativeAddress(baseAddress) }
    }

    private class ExpectedFailure : RuntimeException()

    private companion object {
        const val BASE_ADDRESS = 4096L
    }
}
