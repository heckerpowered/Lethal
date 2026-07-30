/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals

class InterpolatableTest {
    @Test
    fun functionalImplementationReceivesTargetAndAlpha() {
        val start = 10.0
        val interpolatable = Interpolatable<Double> { target, alpha -> start + (target - start) * alpha }

        assertEquals(expected = 10.0, actual = interpolatable.interpolate(20.0, 0.0))
        assertEquals(expected = 15.0, actual = interpolatable.interpolate(20.0, 0.5))
        assertEquals(expected = 20.0, actual = interpolatable.interpolate(20.0, 1.0))
    }
}
