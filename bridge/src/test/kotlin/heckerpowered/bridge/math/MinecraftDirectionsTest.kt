/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.math.Geometry
import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftDirectionsTest {
    @Test
    fun directionsUseMinecraftCoordinates() {
        assertVector(0.0, 1.0, 0.0, MinecraftDirections.Up)
        assertVector(0.0, -1.0, 0.0, MinecraftDirections.Down)
        assertVector(0.0, 0.0, 1.0, MinecraftDirections.Forward)
        assertVector(0.0, 0.0, -1.0, MinecraftDirections.Backward)
        assertVector(-1.0, 0.0, 0.0, MinecraftDirections.Right)
        assertVector(1.0, 0.0, 0.0, MinecraftDirections.Left)
    }

    @Test
    fun vectorAndRotatorConversionsRoundTripMinecraftViewDirections() {
        val rotator = Geometry.rotator(20.0, -35.0)

        val roundTrip = rotator.toViewVector().toRotator()

        assertEquals(expected = rotator.pitch, actual = roundTrip.pitch, absoluteTolerance = 1.0E-12)
        assertEquals(expected = rotator.yaw, actual = roundTrip.yaw, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 0.0, actual = roundTrip.roll)
    }
}
