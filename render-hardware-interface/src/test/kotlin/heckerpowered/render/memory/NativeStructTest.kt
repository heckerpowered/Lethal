/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.memory

import kotlin.test.Test
import kotlin.test.assertEquals

@NativeStruct
internal interface UploadScratch {
    @FloatElements(16)
    val matrix: NativeAddress

    @IntElements(4)
    val viewport: NativeAddress

    @FloatElements(4)
    val color: NativeAddress

    @Bytes(256, alignment = 16)
    val scratch: NativeAddress
}

class NativeStructTest {
    @Test
    fun generatedLayoutMatchesEveryDeclaredField() {
        assertEquals(expected = 0, actual = UploadScratchLayout.MATRIX_OFFSET)
        assertEquals(expected = 64, actual = UploadScratchLayout.VIEWPORT_OFFSET)
        assertEquals(expected = 80, actual = UploadScratchLayout.COLOR_OFFSET)
        assertEquals(expected = 96, actual = UploadScratchLayout.SCRATCH_OFFSET)
        assertEquals(expected = 352, actual = UploadScratchLayout.SIZE)
        assertEquals(expected = 16, actual = UploadScratchLayout.ALIGNMENT)
    }

    @Test
    fun generatedMemoryFrameOverloadReservesWithinTheCurrentFrame() {
        val stack = MemoryStack(1024) { NativeAddress(UNALIGNED_BASE_ADDRESS) }
        stack.frame {
            allocUploadScratch { matrix, viewport, color, scratch ->
                assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS), actual = matrix)
                assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS + 64), actual = viewport)
                assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS + 80), actual = color)
                assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS + 96), actual = scratch)
            }

            assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS + 352), actual = reserve(1, 1))
        }

        stack.frame { assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS), actual = reserve(1, 16)) }
    }

    @Test
    fun generatedMemoryStackOverloadOwnsAndReclaimsItsFrame() {
        val stack = MemoryStack(1024) { NativeAddress(UNALIGNED_BASE_ADDRESS) }
        stack.allocUploadScratch { matrix, _, _, _ ->
            assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS), actual = matrix)
            assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS + 352), actual = reserve(1, 1))
        }

        stack.frame { assertEquals(expected = NativeAddress(ALIGNED_BASE_ADDRESS), actual = reserve(1, 16)) }
    }

    private companion object {
        const val UNALIGNED_BASE_ADDRESS = 4099L
        const val ALIGNED_BASE_ADDRESS = 4112L
    }
}
