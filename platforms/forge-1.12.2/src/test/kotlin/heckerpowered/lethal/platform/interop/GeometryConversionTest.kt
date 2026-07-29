/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.math.BlockDirection
import net.minecraft.util.EnumFacing
import kotlin.test.Test
import kotlin.test.assertEquals

class GeometryConversionTest {
    @Test
    fun blockDirectionsRoundTripThroughNativeDirections() {
        val nativeDirections = BlockDirection.entries.map(BlockDirection::asHost)

        assertEquals(expected = EnumFacing.entries, actual = nativeDirections)
        assertEquals(expected = BlockDirection.entries, actual = nativeDirections.map(EnumFacing::asView))
    }
}
