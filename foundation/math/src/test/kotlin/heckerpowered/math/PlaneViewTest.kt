/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.*

class PlaneViewTest {
    @Test
    fun normalizedFactoryScalesTheOffsetAlongWithTheNormal() {
        val plane = Planes.normalized(0.0, 3.0, 4.0, offset = 10.0)

        assertPlane(0.0, 0.6, 0.8, 2.0, plane)
        assertEquals(0.0, plane.evaluate(Vectors.of(0.0, 2.0, 1.0)), 1.0E-12)
        assertTrue(plane.isNormalized())
        assertPlane(0.0, -0.6, -0.8, -2.0, Planes.normalized(0.0, -3.0, -4.0, offset = -10.0))
    }

    @Test
    fun normalizedFactoryThrowsWhenNormalizationFails() {
        for (normalX in listOf(0.0, 1.0E-6, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Planes.normalized(normalX, 0.0, 0.0, offset = 1.0) }
        }
        assertFailsWith<IllegalArgumentException> { Planes.normalized(1.0, 0.0, 0.0, offset = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Planes.normalized(1.0, 0.0, 0.0, offset = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun factoriesUseTheNormalDotPointEqualsOffsetConvention() {
        val point = Vectors.of(2.0, 3.0, 4.0)
        val plane = Planes.fromPointAndNormal(point, Vectors.of(0.0, 2.0, 0.0))

        assertPlane(0.0, 2.0, 0.0, 6.0, plane)
        assertEquals(0.0, plane.evaluate(point))
        assertEquals(4.0, plane.evaluate(Vectors.of(2.0, 5.0, 4.0)))
        assertEquals(2.0, plane.signedDistanceTo(Vectors.of(2.0, 5.0, 4.0)))
        assertEquals(-2.0, plane.signedDistanceTo(Vectors.of(2.0, 1.0, 4.0)))
        assertEquals(2.0, plane.distanceTo(Vectors.of(2.0, 1.0, 4.0)))
        assertVector(0.0, 3.0, 0.0, plane.origin)
        assertVector(0.0, 2.0, 0.0, plane.normal)
    }

    @Test
    fun threePointsDetermineAUnitNormalWithWindingOrientation() {
        val first = Vectors.of(0.0, 0.0, 2.0)
        val second = Vectors.of(1.0, 0.0, 2.0)
        val third = Vectors.of(0.0, 1.0, 2.0)

        assertPlane(0.0, 0.0, 1.0, 2.0, Planes.fromPoints(first, second, third))
        assertPlane(0.0, 0.0, -1.0, -2.0, Planes.fromPoints(first, third, second))
        assertNull(Planes.fromPointsOrNull(first, second, second))
        assertNull(Planes.fromPointsOrNull(first, second, Vectors.of(Double.NaN, 0.0, 0.0)))
        assertFailsWith<IllegalArgumentException> { Planes.fromPoints(first, second, second) }
    }

    @Test
    fun coefficientOperationsDoNotPretendToBeGeometricComposition() {
        val first = Planes.of(1.0, 2.0, 3.0, 4.0)
        val second = Planes.of(5.0, 6.0, 7.0, 8.0)

        assertPlane(6.0, 8.0, 10.0, 12.0, first + second)
        assertPlane(-4.0, -4.0, -4.0, -4.0, first - second)
        assertPlane(-1.0, -2.0, -3.0, -4.0, -first)
        assertPlane(-1.0, -2.0, -3.0, -4.0, first.flipped())
        assertPlane(2.0, 4.0, 6.0, 8.0, 2.0 * first)
        assertPlane(0.5, 1.0, 1.5, 2.0, first / 2.0)
        assertPlane(3.0, 4.0, 5.0, 6.0, first.interpolate(second, 0.5))
        assertEquals(70.0, first.coefficientDot(second))
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), (0..3).map { first[it] })
        assertFailsWith<IllegalArgumentException> { first[-1] }
        assertFailsWith<IllegalArgumentException> { first[4] }
    }

    @Test
    fun normalizationPreservesThePlaneAndScalesTheOffset() {
        val plane = Planes.of(0.0, 3.0, 4.0, 10.0)

        assertPlane(0.0, 0.6, 0.8, 2.0, plane.normalized())
        assertTrue(plane.normalized().isNormalized())
        assertEquals(25.0, plane.normalLengthSquared)
        assertEquals(5.0, plane.normalLength)
        assertTrue(plane.isSamePlane(plane * 3.0))
        assertTrue(plane.isSamePlane(plane * -3.0))
        assertFalse(plane.isNearlyEqual(plane * 3.0))
        assertFalse(plane.isSamePlane(Planes.of(0.0, 3.0, 4.0, 11.0)))
        assertFalse(Planes.Zero.isSamePlane(Planes.Zero))
    }

    @Test
    fun normalizationHandlesExtremeCoefficientScalesWithoutSquaringOverflowOrUnderflow() {
        assertPlane(1.0, 0.0, 0.0, 2.0, Planes.of(1.0E200, 0.0, 0.0, 2.0E200).normalizedOrNull(0.0)!!)
        assertPlane(1.0, 0.0, 0.0, 2.0, Planes.of(1.0E-200, 0.0, 0.0, 2.0E-200).normalizedOrNull(0.0)!!)
        assertNull(Planes.of(Double.MIN_VALUE, 0.0, 0.0, Double.MAX_VALUE).normalizedOrNull(0.0))
    }

    @Test
    fun degenerateAndNonFinitePlanesHaveExplicitFailurePaths() {
        val invalidPlanes = listOf(
            Planes.Zero,
            Planes.of(0.0, 0.0, 0.0, 1.0),
            Planes.of(Double.NaN, 0.0, 0.0, 0.0),
            Planes.of(1.0, 0.0, 0.0, Double.POSITIVE_INFINITY),
        )
        val fallback = Planes.fromNormal(Vectors.UnitY, 0.0)
        for (plane in invalidPlanes) {
            assertFalse(plane.isValid())
            assertNull(plane.normalizedOrNull(0.0))
            assertSame(fallback, plane.normalizedOr(fallback, 0.0))
            assertFailsWith<IllegalArgumentException> { plane.origin }
            assertFailsWith<IllegalArgumentException> { plane.signedDistanceTo(Vectors.Zero) }
            assertFailsWith<IllegalArgumentException> { plane.projectPosition(Vectors.Zero) }
            assertFailsWith<IllegalArgumentException> { plane.mirrorPosition(Vectors.Zero) }
        }
        assertTrue(invalidPlanes[2].containsNaN())
        assertFalse(invalidPlanes[3].containsNaN())
    }

    @Test
    fun toleranceBoundaryAndInvalidParametersAreConsistent() {
        val plane = Planes.of(0.0, 0.5, 0.0, 1.0)
        assertFalse(plane.isValid(0.25))
        assertNull(plane.normalizedOrNull(0.25))
        assertTrue(plane.isValid(0.0))

        for (invalid in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { plane.isValid(invalid) }
            assertFailsWith<IllegalArgumentException> { plane.normalizedOrNull(invalid) }
            assertFailsWith<IllegalArgumentException> { plane.isNormalized(invalid) }
            assertFailsWith<IllegalArgumentException> { plane.isNearlyEqual(plane, invalid) }
            assertFailsWith<IllegalArgumentException> { plane.isSamePlane(plane, invalid) }
            assertFailsWith<IllegalArgumentException> { plane.transformedByOrNull(Matrices4.Identity, invalid) }
            assertFailsWith<IllegalArgumentException> { Planes.fromPointsOrNull(Vectors.Zero, Vectors.UnitX, Vectors.UnitY, invalid) }
        }
    }

    @Test
    fun projectionAndReflectionSupportNonUnitNormals() {
        val plane = Planes.of(0.0, 2.0, 0.0, 6.0)
        val point = Vectors.of(4.0, 5.0, -2.0)

        assertVector(4.0, 3.0, -2.0, plane.projectPosition(point))
        assertVector(4.0, 1.0, -2.0, plane.mirrorPosition(point))
        assertVector(point, plane.mirrorPosition(plane.mirrorPosition(point)))
        assertVector(plane.projectPosition(point), plane.flipped().projectPosition(point))
    }

    @Test
    fun translationPreservesCoefficientScale() {
        val plane = Planes.of(0.0, 2.0, 0.0, 6.0)
        assertPlane(0.0, 2.0, 0.0, 14.0, plane.translated(Vectors.of(9.0, 4.0, 8.0)))
    }

    @Test
    fun affineTransformationUsesInverseTransposeIncludingOffsetAndReflection() {
        val matrix = Matrices4.of(
            -2.0, 1.0, 0.0, 5.0,
            0.0, 3.0, 0.0, 7.0,
            0.0, 0.0, 4.0, 9.0,
            0.0, 0.0, 0.0, 1.0,
        )
        val plane = Planes.of(2.0, 0.0, 0.0, 4.0)
        val transformed = plane.transformedBy(matrix)

        assertPlane(-1.0, 1.0 / 3.0, 0.0, 4.0 / 3.0, transformed)
        for (point in listOf(Vectors.of(2.0, 0.0, 0.0), Vectors.of(2.0, 3.0, -4.0))) {
            assertEquals(0.0, transformed.evaluate(matrix.transformPosition(point)), 1.0E-12)
        }
        val positivePoint = Vectors.of(3.0, 0.0, 0.0)
        assertEquals(plane.evaluate(positivePoint), transformed.evaluate(matrix.transformPosition(positivePoint)), 1.0E-12)
    }

    @Test
    fun transformationRejectsSingularInvalidAndProjectiveInput() {
        val plane = Planes.fromNormal(Vectors.UnitX, 1.0)
        val singular = Matrices4.fromScale(Vectors.of(0.0, 1.0, 1.0))
        val projective = Matrices4.Identity.withComponent(3, 0, 1.0)

        assertNull(plane.transformedByOrNull(singular))
        assertNull(Planes.Zero.transformedByOrNull(Matrices4.Identity))
        assertFailsWith<IllegalArgumentException> { plane.transformedBy(singular) }
        assertFailsWith<IllegalArgumentException> { plane.transformedByOrNull(projective) }
        assertNull(plane.transformedByOrNull(Matrices4.Identity.withComponent(0, 0, Double.NaN)))
    }

    @Test
    fun geometryProvidersHaveAFreestandingPlaneFallback() {
        val defaultProvider = object : GeometryProvider {}
        val planes = listOf(
            defaultProvider.plane(1.0, 2.0, 3.0, 4.0),
            FreestandingGeometryProvider.plane(1.0, 2.0, 3.0, 4.0),
            Geometry.plane(1.0, 2.0, 3.0, 4.0),
        )
        for (plane in planes) {
            assertPlane(1.0, 2.0, 3.0, 4.0, plane)
        }
    }

    @Test
    fun checkedOperationsSampleEachPlaneCoefficientOnce() {
        val operations: List<(PlaneView) -> Unit> = listOf(
            { assertPlane(0.0, 2.0, 0.0, 6.0, Planes.copyOf(it)) },
            { assertTrue(it.isValid()) },
            { assertPlane(0.0, 1.0, 0.0, 3.0, it.normalizedOrNull()!!) },
            { assertVector(0.0, 3.0, 0.0, it.origin) },
            { assertEquals(1.0, it.signedDistanceTo(Vectors.of(0.0, 4.0, 0.0))) },
            { assertVector(0.0, 3.0, 0.0, it.projectPosition(Vectors.Zero)) },
            { assertVector(0.0, 6.0, 0.0, it.mirrorPosition(Vectors.Zero)) },
            { assertPlane(0.0, 2.0, 0.0, 8.0, it.translated(Vectors.UnitY)) },
            { assertPlane(0.0, 2.0, 0.0, 6.0, it.transformedBy(Matrices4.Identity)) },
        )
        for (operation in operations) {
            val plane = SampleOncePlane()
            operation(plane)
            assertEquals(listOf(1, 1, 1, 1), plane.reads.toList())
        }
    }

    @Test
    fun transformationValidatesAndInvertsTheSameMatrixSnapshot() {
        var translationReads = 0
        var affineReads = 0
        val matrix = object : MatrixView by Matrices4.Identity {
            override val m13: Double get() = if (++translationReads == 1) 4.0 else Double.NaN
            override val m33: Double get() = if (++affineReads == 1) 1.0 else Double.NaN
        }

        assertPlane(0.0, 2.0, 0.0, 14.0, Planes.of(0.0, 2.0, 0.0, 6.0).transformedBy(matrix))
        assertEquals(1, translationReads)
        assertEquals(1, affineReads)
    }

    private class SampleOncePlane : PlaneView {
        val reads = IntArray(4)
        override val x: Double get() = read(0, 0.0)
        override val y: Double get() = read(1, 2.0)
        override val z: Double get() = read(2, 0.0)
        override val w: Double get() = read(3, 6.0)

        private fun read(index: Int, value: Double): Double = if (++reads[index] == 1) value else Double.NaN
    }

    private fun assertPlane(x: Double, y: Double, z: Double, w: Double, actual: PlaneView) {
        assertEquals(x, actual.x, 1.0E-12, "x")
        assertEquals(y, actual.y, 1.0E-12, "y")
        assertEquals(z, actual.z, 1.0E-12, "z")
        assertEquals(w, actual.w, 1.0E-12, "w")
    }
}
