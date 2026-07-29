/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BloomPyramidTest {
    @Test
    fun `uses the Matrix mip level count for power of two dimensions`() {
        assertEquals(
            expected = listOf(
                FramebufferDimensions(256, 128),
                FramebufferDimensions(128, 64),
                FramebufferDimensions(64, 32),
                FramebufferDimensions(32, 16),
                FramebufferDimensions(16, 8),
                FramebufferDimensions(8, 4),
                FramebufferDimensions(4, 2),
                FramebufferDimensions(2, 1),
            ),
            actual = bloomPyramidDimensions(256, 128),
        )
    }

    @Test
    fun `scales non power of two dimensions to one pixel`() {
        assertEquals(expected = FramebufferDimensions(1, 1), actual = bloomPyramidDimensions(1920, 1080).last())
    }

    @Test
    fun `rejects non positive dimensions`() {
        assertFailsWith<IllegalArgumentException> { bloomPyramidDimensions(0, 1080) }
    }
}
