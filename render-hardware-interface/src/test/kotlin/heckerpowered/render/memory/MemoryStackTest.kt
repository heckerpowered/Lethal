/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoryStackTest {
    @Test
    fun nestedFramesReclaimOnlyTheirOwnAllocations() {
        stackAlloc {
            val outerValue = ints(1)
            outerValue.put(0, 11)

            val nestedValue = stackAlloc {
                ints(1).apply { put(0, 22) }
            }

            val reusedValue = ints(1)
            reusedValue.put(0, 33)

            assertEquals(expected = 11, actual = outerValue.get(0))
            assertEquals(expected = 33, actual = nestedValue.get(0))
        }
    }

    @Test
    fun allocationBeyondFixedCapacityFailsWithoutReplacingMemory() {
        stackAlloc {
            val exception = assertFailsWith<IllegalStateException> { bytes(64 * 1024 + 1) }
            assertEquals(expected = "Memory stack overflow: requested=65537 bytes, remaining=65536 bytes, capacity=65536 bytes", actual = exception.message)

            val value = ints(1)
            value.put(0, 42)
            assertEquals(expected = 42, actual = value.get(0))
        }
    }

    @Test
    fun frameDepthBeyondFixedCapacityFails() {
        fun allocateFrames(remainingFrameCount: Int) {
            if (remainingFrameCount == 0) {
                val exception = assertFailsWith<IllegalStateException> { stackAlloc {} }
                assertEquals(expected = "Memory stack frame overflow: maximum depth=16", actual = exception.message)
                return
            }

            stackAlloc { allocateFrames(remainingFrameCount - 1) }
        }

        allocateFrames(16)
        stackAlloc { assertEquals(expected = 1, actual = ints(1).capacity()) }
    }
}
