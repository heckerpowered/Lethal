/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertEquals

class RotatorViewTest {
    @Test
    fun rotatorPreservesPitchYawAndRoll() {
        val rotator = Geometry.rotator(-30.0, 45.0, 12.0)

        assertEquals(expected = -30.0, actual = rotator.pitch)
        assertEquals(expected = 45.0, actual = rotator.yaw)
        assertEquals(expected = 12.0, actual = rotator.roll)
    }
}
