/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.math.Geometry
import kotlin.test.Test

class MinecraftRotatorTest {

    @Test
    fun viewVectorUsesMinecraftYawConvention() {
        assertVector(0.0, 0.0, 1.0, Geometry.rotator(0.0, 0.0).toViewVector(), 1.0E-12)
        assertVector(1.0, 0.0, 0.0, Geometry.rotator(0.0, -90.0).toViewVector(), 1.0E-12)
        assertVector(-1.0, 0.0, 0.0, Geometry.rotator(0.0, 90.0).toViewVector(), 1.0E-12)
        assertVector(0.0, 0.0, -1.0, Geometry.rotator(0.0, 180.0).toViewVector(), 1.0E-12)
    }

    @Test
    fun viewVectorUsesMinecraftPitchConvention() {
        assertVector(0.0, 1.0, 0.0, Geometry.rotator(-90.0, 0.0).toViewVector(), 1.0E-12)
        assertVector(0.0, -1.0, 0.0, Geometry.rotator(90.0, 0.0).toViewVector(), 1.0E-12)
    }

    @Test
    fun rollDoesNotChangeViewDirection() {
        val withoutRoll = Geometry.rotator(25.0, -35.0).toViewVector()
        val withRoll = Geometry.rotator(25.0, -35.0, 90.0).toViewVector()

        assertVector(withoutRoll.x, withoutRoll.y, withoutRoll.z, withRoll, 1.0E-12)
    }
}
