/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.assertEquals

internal fun assertVector(expectedX: Double, expectedY: Double, expectedZ: Double, actual: VectorView, absoluteTolerance: Double = 0.0) {
    assertEquals(expected = expectedX, actual = actual.x, absoluteTolerance = absoluteTolerance, message = "x")
    assertEquals(expected = expectedY, actual = actual.y, absoluteTolerance = absoluteTolerance, message = "y")
    assertEquals(expected = expectedZ, actual = actual.z, absoluteTolerance = absoluteTolerance, message = "z")
}

internal fun assertBox(expectedMinX: Double, expectedMinY: Double, expectedMinZ: Double, expectedMaxX: Double, expectedMaxY: Double, expectedMaxZ: Double, actual: BoxView) {
    assertEquals(expected = expectedMinX, actual = actual.minX, message = "minX")
    assertEquals(expected = expectedMinY, actual = actual.minY, message = "minY")
    assertEquals(expected = expectedMinZ, actual = actual.minZ, message = "minZ")
    assertEquals(expected = expectedMaxX, actual = actual.maxX, message = "maxX")
    assertEquals(expected = expectedMaxY, actual = actual.maxY, message = "maxY")
    assertEquals(expected = expectedMaxZ, actual = actual.maxZ, message = "maxZ")
}

internal fun assertBlockPosition(expectedX: Int, expectedY: Int, expectedZ: Int, actual: BlockPositionView) {
    assertEquals(expected = expectedX, actual = actual.x, message = "x")
    assertEquals(expected = expectedY, actual = actual.y, message = "y")
    assertEquals(expected = expectedZ, actual = actual.z, message = "z")
}

internal fun VectorView.coordinates(): Triple<Double, Double, Double> = Triple(x, y, z)

internal fun BlockPositionView.coordinates(): Triple<Int, Int, Int> = Triple(x, y, z)
