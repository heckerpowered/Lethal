/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

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

internal fun VectorView.coordinates(): Triple<Double, Double, Double> = Triple(x, y, z)

internal fun assertVector(expected: VectorView, actual: VectorView, absoluteTolerance: Double = 1.0E-12) {
    assertVector(expected.x, expected.y, expected.z, actual, absoluteTolerance)
}

internal fun assertMatrix(expected: MatrixView, actual: MatrixView, absoluteTolerance: Double = 1.0E-12) {
    for (row in 0..3) {
        for (column in 0..3) {
            assertEquals(expected[row, column], actual[row, column], absoluteTolerance, "[$row, $column]")
        }
    }
}
