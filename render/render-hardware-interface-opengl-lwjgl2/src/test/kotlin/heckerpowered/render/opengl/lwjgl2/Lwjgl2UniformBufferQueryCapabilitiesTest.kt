/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Lwjgl2UniformBufferQueryCapabilitiesTest {
    @Test
    fun optionalPointerHasAConservativePredictableFallback() {
        assertFalse(hasIndexedUniformRangeQuery(Any()))
        assertFalse(hasIndexedUniformRangeQuery(Pointer(0)))
        assertFalse(hasIndexedUniformRangeQuery(WrongType()))
        assertTrue(hasIndexedUniformRangeQuery(Pointer(17)))
    }
}

private class Pointer(private val glGetInteger64i_v: Long)
private class WrongType(private val glGetInteger64i_v: Int = 17)
