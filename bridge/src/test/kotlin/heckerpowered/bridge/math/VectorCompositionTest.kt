/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.test.Test
import kotlin.test.assertEquals

class VectorCompositionTest {
    @Test
    fun normalAndPlanarProjectionsReconstructTheOriginalVector() {
        val vector = Geometry.vector(3.0, -4.0, 5.0)
        val normal = Geometry.vector(2.0, 1.0, -2.0).normalized()

        val normalComponent = vector.projectOntoNormal(normal)
        val planarComponent = vector.projectOntoPlane(normal)

        assertVector(vector.x, vector.y, vector.z, normalComponent + planarComponent, 1.0E-12)
        assertEquals(expected = 0.0, actual = planarComponent.dot(normal), absoluteTolerance = 1.0E-12)
        assertVector(0.0, 0.0, 0.0, normalComponent.cross(normal), 1.0E-12)
        assertVector(normalComponent.x, normalComponent.y, normalComponent.z, vector.projectOnto(normal), 1.0E-12)
    }

    @Test
    fun arbitraryAxisRotationPreservesLengthAndAxialProjection() {
        val vector = Geometry.vector(-4.0, 5.0, 2.0)
        val axis = Geometry.vector(1.0, 2.0, 3.0).normalized()

        val rotated = vector.rotateAngleAxis(137.0, axis)
        val restored = rotated.rotateAngleAxis(-137.0, axis)

        assertEquals(expected = vector.length, actual = rotated.length, absoluteTolerance = 1.0E-12)
        assertEquals(expected = vector.dot(axis), actual = rotated.dot(axis), absoluteTolerance = 1.0E-12)
        assertVector(vector.x, vector.y, vector.z, restored, 1.0E-12)
    }

    @Test
    fun mirroringTwiceAcrossAUnitNormalRestoresTheVector() {
        val vector = Geometry.vector(-4.0, 5.0, 2.0)
        val normal = Geometry.vector(1.0, -2.0, 3.0).normalized()

        val restored = vector.mirrorByVector(normal).mirrorByVector(normal)

        assertVector(vector.x, vector.y, vector.z, restored, 1.0E-12)
    }

    @Test
    fun crossProductIsOrthogonalAndAnticommutative() {
        val first = Geometry.vector(2.0, -3.0, 5.0)
        val second = Geometry.vector(-7.0, 11.0, 13.0)

        val cross = first.cross(second)

        assertEquals(expected = 0.0, actual = cross.dot(first), absoluteTolerance = 1.0E-12)
        assertEquals(expected = 0.0, actual = cross.dot(second), absoluteTolerance = 1.0E-12)
        assertVector(cross.x, cross.y, cross.z, -second.cross(first), 1.0E-12)
    }

    @Test
    fun perpendicularBasesReconstructVectorsForBothTemporaryAxisBranches() {
        val directions = listOf(Geometry.vector(0.1, 0.2, 1.0), Geometry.vector(1.0, 0.2, 0.1))

        for (direction in directions) {
            val basis = direction.createPerpendicularBasis()
            val worldVector = basis.right * 2.0 + basis.up * -3.0 + basis.forward * 4.0

            assertEquals(expected = 2.0, actual = worldVector.dot(basis.right), absoluteTolerance = 1.0E-12)
            assertEquals(expected = -3.0, actual = worldVector.dot(basis.up), absoluteTolerance = 1.0E-12)
            assertEquals(expected = 4.0, actual = worldVector.dot(basis.forward), absoluteTolerance = 1.0E-12)
        }
    }

    @Test
    fun interpolationComposesAlongTheSameAffineLine() {
        val start = Geometry.vector(-2.0, 4.0, 8.0)
        val end = Geometry.vector(10.0, -2.0, 2.0)

        val midpoint = start.interpolate(end, 0.5)
        val recomposed = start.interpolate(midpoint, 0.5)

        assertVector(4.0, 1.0, 5.0, midpoint)
        assertVector(1.0, 2.5, 6.5, recomposed)
        assertVector(-8.0, 7.0, 11.0, start.interpolate(end, -0.5))
        assertVector(16.0, -5.0, -1.0, start.interpolate(end, 1.5))
    }
}
