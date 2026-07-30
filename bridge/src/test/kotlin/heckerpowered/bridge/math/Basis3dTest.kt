/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class Basis3dTest {
    @Test
    fun basisPreservesItsThreeAxes() {
        val basis = Basis3d(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ)

        assertSame(expected = Vectors.UnitX, actual = basis.right)
        assertSame(expected = Vectors.UnitY, actual = basis.up)
        assertSame(expected = Vectors.UnitZ, actual = basis.forward)
    }

    @Test
    fun perpendicularBasisIsOrthonormalAndKeepsForwardDirection() {
        val direction = Geometry.vector(2.0, -3.0, 4.0).normalized()

        val basis = direction.createPerpendicularBasis()

        assertEquals(expected = 1.0, actual = basis.right.length, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 1.0, actual = basis.up.length, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 1.0, actual = basis.forward.length, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 0.0, actual = basis.right.dot(basis.up), absoluteTolerance = 1.0E-12)
        assertEquals(expected = 0.0, actual = basis.right.dot(basis.forward), absoluteTolerance = 1.0E-12)
        assertEquals(expected = 0.0, actual = basis.up.dot(basis.forward), absoluteTolerance = 1.0E-12)
        assertVector(direction.x, direction.y, direction.z, basis.forward, 1.0E-12)
        assertVector(basis.up.x, basis.up.y, basis.up.z, basis.forward.cross(basis.right), 1.0E-12)
    }
}
