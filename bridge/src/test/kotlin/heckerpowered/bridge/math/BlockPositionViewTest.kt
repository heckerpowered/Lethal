/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockPositionViewTest {
    @Test
    fun componentsExposeAxesAndImmutableIdentity() {
        val position = BlockPositions.of(12, -34, 56)

        assertEquals(expected = 12, actual = position[0])
        assertEquals(expected = -34, actual = position[1])
        assertEquals(expected = 56, actual = position[2])
        assertFalse(position.isZero())
        assertTrue(BlockPositions.Zero.isZero())
        assertBlockPosition(12, -34, 56, position.immutable())
        assertFailsWith<IllegalArgumentException> { position[-1] }
        assertFailsWith<IllegalArgumentException> { position[3] }
    }

    @Test
    fun canonicalPositionsExposeZeroOneAndPositiveAxes() {
        assertBlockPosition(0, 0, 0, BlockPositions.Zero)
        assertBlockPosition(1, 1, 1, BlockPositions.One)
        assertBlockPosition(1, 0, 0, BlockPositions.UnitX)
        assertBlockPosition(0, 1, 0, BlockPositions.UnitY)
        assertBlockPosition(0, 0, 1, BlockPositions.UnitZ)
    }

    @Test
    fun axisOffsetsAndNamedDirectionsProduceExpectedCoordinates() {
        val position = BlockPositions.of(1, 2, 3)

        assertBlockPosition(5, 1, 5, position.offset(4, -1, 2))
        assertBlockPosition(1, 4, 3, position.above(2))
        assertBlockPosition(1, 0, 3, position.below(2))
        assertBlockPosition(1, 2, 1, position.north(2))
        assertBlockPosition(1, 2, 5, position.south(2))
        assertBlockPosition(-1, 2, 3, position.west(2))
        assertBlockPosition(3, 2, 3, position.east(2))
        assertBlockPosition(1, 5, 3, position.offset(BlockDirection.Up, 3))
        assertBlockPosition(1, 2, 3, position.offset(0, 0, 0))
        assertBlockPosition(1, 2, 3, position.offset(BlockDirection.Up, 0))
    }

    @Test
    fun componentReplacementArithmeticAndCrossProductAreCoordinateWise() {
        val first = BlockPositions.of(1, 2, 3)
        val second = BlockPositions.of(4, -5, 6)

        assertBlockPosition(8, 2, 3, first.withX(8))
        assertBlockPosition(1, 8, 3, first.withY(8))
        assertBlockPosition(1, 2, 8, first.withZ(8))
        assertBlockPosition(5, -3, 9, first + second)
        assertBlockPosition(-3, 7, -3, first - second)
        assertBlockPosition(-1, -2, -3, -first)
        assertBlockPosition(3, 6, 9, first * 3)
        assertBlockPosition(27, 6, -13, first.cross(second))
        assertBlockPosition(1, -5, 3, first.componentMin(second))
        assertBlockPosition(4, 2, 6, first.componentMax(second))
        assertBlockPosition(1, 2, 3, first * 1)
        assertBlockPosition(0, 0, 0, first * 0)
    }

    @Test
    fun rotationKeepsVerticalAxisAndRotatesHorizontalCoordinates() {
        val position = BlockPositions.of(2, 7, 3)

        assertBlockPosition(2, 7, 3, position.rotate(BlockRotation.None))
        assertBlockPosition(-3, 7, 2, position.rotate(BlockRotation.Clockwise90))
        assertBlockPosition(-2, 7, -3, position.rotate(BlockRotation.Clockwise180))
        assertBlockPosition(3, 7, -2, position.rotate(BlockRotation.Counterclockwise90))
    }

    @Test
    fun distanceOperationsUseThreeDimensionalBlockCoordinates() {
        val first = BlockPositions.of(1, 2, 3)
        val second = BlockPositions.of(4, -2, 15)

        assertEquals(expected = 169L, actual = first.distanceSquaredTo(second))
        assertEquals(expected = 19, actual = first.manhattanDistanceTo(second))
        assertEquals(expected = 169L, actual = BlockPositions.distanceSquared(first, second))
        assertEquals(expected = 19, actual = BlockPositions.manhattanDistance(first, second))
        assertBlockPosition(1, -2, 3, BlockPositions.min(first, second))
        assertBlockPosition(4, 2, 15, BlockPositions.max(first, second))
        assertBlockPosition(36, -3, -10, BlockPositions.cross(first, second))
    }

    @Test
    fun betweenClosedIncludesBothBoundsInXThenYThenZOrder() {
        val positions = BlockPositions.betweenClosed(BlockPositions.of(1, 1, 0), BlockPositions.of(0, 0, 1))
            .map(BlockPositionView::coordinates)
            .toList()

        assertEquals(
            expected = listOf(
                Triple(0, 0, 0), Triple(1, 0, 0),
                Triple(0, 1, 0), Triple(1, 1, 0),
                Triple(0, 0, 1), Triple(1, 0, 1),
                Triple(0, 1, 1), Triple(1, 1, 1),
            ),
            actual = positions,
        )
    }

    @Test
    fun firstBetweenClosedStopsAtTheFirstMatchingPosition() {
        val inspected = mutableListOf<Triple<Int, Int, Int>>()

        val result = BlockPositions.firstBetweenClosed(BlockPositions.of(0, 0, 0), BlockPositions.of(2, 1, 0)) { position ->
            inspected += position.coordinates()
            position.x == 1
        }

        assertBlockPosition(1, 0, 0, requireNotNull(result))
        assertEquals(expected = listOf(Triple(0, 0, 0), Triple(1, 0, 0)), actual = inspected)
        assertNull(BlockPositions.firstBetweenClosed(BlockPositions.Zero, BlockPositions.One) { false })
    }

    @Test
    fun coordinateBetweenClosedOverloadMatchesPositionOverload() {
        val expected = BlockPositions.betweenClosed(BlockPositions.of(-1, 2, 3), BlockPositions.of(1, 3, 4)).map(BlockPositionView::coordinates).toList()
        val actual = BlockPositions.betweenClosed(-1, 2, 3, 1, 3, 4).map(BlockPositionView::coordinates).toList()

        assertEquals(expected = expected, actual = actual)
    }

    @Test
    fun defaultPackingExtensionsRoundTripThePosition() {
        val position = BlockPositions.of(12, -34, 56)

        assertEquals(expected = position.asLong(), actual = position.pack())
        assertBlockPosition(12, -34, 56, BlockPositions.fromPackedLong(position.pack()))
    }

    @Test
    fun withinManhattanVisitsTheBoundedVolumeByIncreasingDistance() {
        val origin = BlockPositions.of(10, 20, 30)
        val positions = BlockPositions.withinManhattan(origin, 1, 1, 1).toList()
        val coordinates = positions.map(BlockPositionView::coordinates)
        val distances = positions.map(origin::manhattanDistanceTo)

        assertEquals(expected = 27, actual = positions.size)
        assertEquals(expected = 27, actual = coordinates.toSet().size)
        assertEquals(expected = Triple(10, 20, 30), actual = coordinates.first())
        assertTrue(positions.all { position -> position.x in 9..11 && position.y in 19..21 && position.z in 29..31 })
        assertTrue(distances.zipWithNext().all { (first, second) -> first <= second })

        val overloadCoordinates = BlockPositions.withinManhattan(10, 20, 30, 1, 0, 1).map(BlockPositionView::coordinates).toList()
        assertEquals(expected = 9, actual = overloadCoordinates.size)
        assertEquals(expected = 9, actual = overloadCoordinates.toSet().size)
    }
}
