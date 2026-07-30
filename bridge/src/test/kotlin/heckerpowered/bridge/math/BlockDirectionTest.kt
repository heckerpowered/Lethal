/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals

class BlockDirectionTest {
    @Test
    fun directionsExposeMinecraftAxisSteps() {
        val expectedSteps = mapOf(
            BlockDirection.Down to Triple(0, -1, 0),
            BlockDirection.Up to Triple(0, 1, 0),
            BlockDirection.North to Triple(0, 0, -1),
            BlockDirection.South to Triple(0, 0, 1),
            BlockDirection.West to Triple(-1, 0, 0),
            BlockDirection.East to Triple(1, 0, 0),
        )

        val actualSteps = BlockDirection.entries.associateWith { direction -> Triple(direction.stepX, direction.stepY, direction.stepZ) }

        assertEquals(expected = expectedSteps, actual = actualSteps)
    }

    @Test
    fun everyDirectionHasASymmetricOpposite() {
        val expectedOpposites = mapOf(
            BlockDirection.Down to BlockDirection.Up,
            BlockDirection.Up to BlockDirection.Down,
            BlockDirection.North to BlockDirection.South,
            BlockDirection.South to BlockDirection.North,
            BlockDirection.West to BlockDirection.East,
            BlockDirection.East to BlockDirection.West,
        )

        assertEquals(expected = expectedOpposites, actual = BlockDirection.entries.associateWith(BlockDirection::opposite))
        BlockDirection.entries.forEach { direction -> assertEquals(expected = direction, actual = direction.opposite.opposite) }
    }
}
