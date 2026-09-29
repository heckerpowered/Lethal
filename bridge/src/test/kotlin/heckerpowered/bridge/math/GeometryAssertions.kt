/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.math.VectorView

import kotlin.test.assertEquals

internal fun assertVector(expectedX: Double, expectedY: Double, expectedZ: Double, actual: VectorView, absoluteTolerance: Double = 0.0) {
    assertEquals(expected = expectedX, actual = actual.x, absoluteTolerance = absoluteTolerance, message = "x")
    assertEquals(expected = expectedY, actual = actual.y, absoluteTolerance = absoluteTolerance, message = "y")
    assertEquals(expected = expectedZ, actual = actual.z, absoluteTolerance = absoluteTolerance, message = "z")
}

internal fun assertBlockPosition(expectedX: Int, expectedY: Int, expectedZ: Int, actual: BlockPositionView) {
    assertEquals(expected = expectedX, actual = actual.x, message = "x")
    assertEquals(expected = expectedY, actual = actual.y, message = "y")
    assertEquals(expected = expectedZ, actual = actual.z, message = "z")
}

internal fun BlockPositionView.coordinates(): Triple<Int, Int, Int> = Triple(x, y, z)
