/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BasisTest {
    @Test
    fun basisPreservesItsThreeAxes() {
        val basis: BasisView = Basis(right = Vectors.UnitX, up = Vectors.UnitY, forward = Vectors.UnitZ)

        assertSame(expected = Vectors.UnitX, actual = basis.right)
        assertSame(expected = Vectors.UnitY, actual = basis.up)
        assertSame(expected = Vectors.UnitZ, actual = basis.forward)
    }

    @Test
    fun perpendicularBasisIsOrthonormalAndKeepsForwardDirection() {
        val direction = Vector(2.0, -3.0, 4.0).normalized()

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

    @Test
    fun basisMatrixRetainsScaledAndShearedColumns() {
        val right = Vector(2.0, 3.0, 4.0)
        val up = Vector(5.0, 6.0, 7.0)
        val forward = Vector(8.0, 9.0, 10.0)
        val basis = Basis(right, up, forward)
        val matrix = Matrices.fromBasis(basis, Vector(11.0, 12.0, 13.0))

        assertSame(right, basis.right)
        assertSame(up, basis.up)
        assertSame(forward, basis.forward)
        assertEquals(
            listOf(2.0, 5.0, 8.0, 11.0, 3.0, 6.0, 9.0, 12.0, 4.0, 7.0, 10.0, 13.0, 0.0, 0.0, 0.0, 1.0),
            matrix.toList(),
        )
        val restored = matrix.toBasis()
        assertVector(right, restored.right)
        assertVector(up, restored.up)
        assertVector(forward, restored.forward)
    }

    @Test
    fun basisRoundTripPreservesQuaternionRotationAndAxisOrder() {
        val rotation = Quaternions.fromAxisAngleDegrees(Vectors.UnitZ, 90.0)
        val basis = rotation.toBasis()

        assertVector(rotation * Vectors.UnitX, basis.right)
        assertVector(rotation * Vectors.UnitY, basis.up)
        assertVector(rotation * Vectors.UnitZ, basis.forward)
        assertTrue(rotation.isSameRotation(Quaternions.fromBasis(basis), 1.0E-12))
        val matrixBasis = Matrices.fromRotation(rotation).toBasis()
        assertVector(basis.right, matrixBasis.right)
        assertVector(basis.up, matrixBasis.up)
        assertVector(basis.forward, matrixBasis.forward)
    }
}
