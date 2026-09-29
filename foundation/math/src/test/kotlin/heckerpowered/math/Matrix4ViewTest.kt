/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.sqrt
import kotlin.test.*

class Matrix4ViewTest {
    private val affine = Matrices4.of(
        2.0, 1.0, 0.0, 4.0,
        0.0, 3.0, 0.0, -3.0,
        0.0, 0.0, -4.0, 2.0,
        0.0, 0.0, 0.0, 1.0
    )

    private val projective = Matrices4.of(
        2.0, 0.0, 0.0, 4.0,
        0.0, 3.0, 0.0, -3.0,
        0.0, 0.0, 4.0, 2.0,
        0.0, 0.0, 1.0, 1.0
    )

    @Test
    fun storageOrdersAndIterationPreserveAllSixteenCoordinates() {
        val rowMajor = DoubleArray(16) { it.toDouble() }
        val matrix = Matrices4.fromRowMajor(rowMajor)
        val columnMajor = doubleArrayOf(
            0.0, 4.0, 8.0, 12.0,
            1.0, 5.0, 9.0, 13.0,
            2.0, 6.0, 10.0, 14.0,
            3.0, 7.0, 11.0, 15.0
        )
        assertContentEquals(rowMajor, matrix.toRowMajorArray())
        assertContentEquals(columnMajor, matrix.toColumnMajorArray())
        assertMatrix(matrix, Matrices4.fromColumnMajor(columnMajor))
        val iterator: DoubleIterator = matrix.iterator()
        for (value in rowMajor) {
            assertTrue(iterator.hasNext())
            assertEquals(value, iterator.nextDouble())
        }
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.nextDouble() }
        rowMajor.fill(-1.0)
        columnMajor.fill(-1.0)
        matrix.toRowMajorArray().fill(-1.0)
        matrix.toColumnMajorArray().fill(-1.0)
        assertEquals(15.0, matrix[3, 3])
        assertEquals(6.0, matrix[1, 2])
    }

    @Test
    fun indicesAndArraySizesRejectInvalidArguments() {
        for (index in listOf(-1, 4, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { affine[index, 0] }
            assertFailsWith<IllegalArgumentException> { affine[0, index] }
            assertFailsWith<IllegalArgumentException> { affine.withComponent(index, 0, 1.0) }
            assertFailsWith<IllegalArgumentException> { affine.withComponent(0, index, 1.0) }
        }
        for (index in listOf(-1, 3)) {
            assertFailsWith<IllegalArgumentException> { affine.axis(index) }
            assertFailsWith<IllegalArgumentException> { affine.unitAxis(index) }
        }
        for (size in listOf(0, 15, 17)) {
            assertFailsWith<IllegalArgumentException> { Matrices4.fromRowMajor(DoubleArray(size)) }
            assertFailsWith<IllegalArgumentException> { Matrices4.fromColumnMajor(DoubleArray(size)) }
        }
    }

    @Test
    fun predicatesDistinguishAffineIdentityZeroAndNonFiniteValues() {
        assertTrue(Matrices4.Zero.isZero())
        assertFalse(Matrices4.Zero.isAffine())
        assertTrue(Matrices4.Identity.isIdentity())
        assertTrue(affine.isAffine())
        assertFalse(projective.isAffine())
        assertTrue(affine.isFinite())
        assertFalse(affine.withComponent(0, 0, Double.POSITIVE_INFINITY).isFinite())
        assertFalse(affine.withComponent(0, 0, Double.POSITIVE_INFINITY).containsNaN())
        assertTrue(affine.withComponent(0, 0, Double.NaN).containsNaN())
        val perturbed = Matrices4.Identity.withComponent(3, 0, 0.125)
        assertFalse(perturbed.isAffine())
        assertTrue(perturbed.isAffine(0.125))
        assertTrue(perturbed.isIdentity(0.125))
        assertTrue(perturbed.isNearlyEqual(Matrices4.Identity, 0.125))
        assertFalse(perturbed.isNearlyEqual(Matrices4.Identity, 0.124))
    }

    @Test
    fun invalidTolerancesAreRejectedAcrossTheMatrixApi() {
        for (tolerance in listOf(-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { affine.isNearlyEqual(affine, tolerance) }
            assertFailsWith<IllegalArgumentException> { affine.isIdentity(tolerance) }
            assertFailsWith<IllegalArgumentException> { affine.isAffine(tolerance) }
            assertFailsWith<IllegalArgumentException> { affine.inverseOrNull(tolerance) }
            assertFailsWith<IllegalArgumentException> { affine.inverse(tolerance) }
            assertFailsWith<IllegalArgumentException> { affine.safeInverse(tolerance) }
            assertFailsWith<IllegalArgumentException> { affine.withNormalizedAxes(tolerance) }
        }
    }

    @Test
    fun componentArithmeticIncludesTheProjectiveRowAndExtrapolates() {
        val first = Matrices4.fromRowMajor(DoubleArray(16) { it.toDouble() })
        val second = Matrices4.fromRowMajor(DoubleArray(16) { 16.0 - it })
        val sum = Matrices4.fromRowMajor(DoubleArray(16) { 16.0 })
        assertMatrix(sum, first + second)
        assertMatrix(first, (first + second) - second)
        assertMatrix(Matrices4.Zero, first + -first)
        assertMatrix(first * 2.0, 2.0 * first)
        assertMatrix(first, (first * 2.0) / 2.0)
        assertMatrix(sum / 2.0, first.interpolate(second, 0.5))
        assertMatrix(first * 2.0 - second, Matrices4.lerp(first, second, -1.0))
        assertMatrix(first, first.transposed().transposed())
        assertEquals(first[1, 3], first.transposed()[3, 1])
        val changed = first.withComponent(3, 2, -8.0)
        assertEquals(-8.0, changed[3, 2])
        assertEquals(14.0, first[3, 2])
        assertEquals(first[2, 3], changed[2, 3])
    }

    @Test
    fun matrixCompositionAndNamedSpaceOperationsUseTheIntendedOrder() {
        val rotation = Matrices4.fromRotation(Quaternions.fromAxisAngleDegrees(Vectors.UnitZ, 90.0))
        val translation = Matrices4.fromTranslation(Vectors.of(10.0, 20.0, 30.0))
        val scale = Vectors.of(2.0, 3.0, 4.0)
        assertVector(-3.0, 2.0, 4.0, rotation.scaledLocal(scale).transformVector(Vectors.One), 1e-12)
        assertVector(9.0, 21.0, 31.0, rotation.translatedWorld(Vectors.of(10.0, 20.0, 30.0)).transformPosition(Vectors.One), 1e-12)
        assertVector(translation.transformPosition(rotation.transformPosition(Vectors.One)), (translation * rotation).transformPosition(Vectors.One))
        assertFalse((translation * rotation).isNearlyEqual(rotation * translation))
        val movedProjection = projective.translatedWorld(Vectors.UnitX)
        assertVector(projective.projectPosition(Vectors.One) + Vectors.UnitX, movedProjection.projectPosition(Vectors.One))
    }

    @Test
    fun affineAndProjectiveTransformsHaveDifferentHomogeneousContracts() {
        val point = Vectors.of(2.0, 3.0, 1.0)
        assertVector(11.0, 6.0, -2.0, affine.transformPosition(point))
        assertVector(7.0, 9.0, -4.0, affine.transformVector(point))
        assertVector(4.0, 3.0, 3.0, projective.projectPosition(point))
        assertVector(8.0, 6.0, 6.0, projective.transformPosition(point))
        assertVector(point, projective.inverse().projectPosition(projective.projectPosition(point)))
        assertFailsWith<IllegalArgumentException> { projective.inverseTransformPosition(point) }
        assertFailsWith<IllegalArgumentException> { projective.inverseTransformVector(point) }
        assertFailsWith<IllegalArgumentException> { projective.toAffine() }
        assertFailsWith<IllegalArgumentException> { projective.transformNormal(point) }
        assertNull(projective.projectPositionOrNull(Vectors.of(1.0, 1.0, -1.0)))
        assertFalse(projective.projectPosition(Vectors.of(1.0, 1.0, -1.0)).isFinite())
        assertNotNull(projective.projectPositionOrNull(point))
    }

    @Test
    fun determinantsAndInversesHandleRowSwapsShearAndPerspective() {
        val swapped = Matrices4.of(
            0.0, 2.0, 0.0, 1.0,
            1.0, 0.0, 0.0, 2.0,
            0.0, 0.0, 3.0, 0.0,
            0.0, 0.0, 1.0, 1.0
        )
        assertEquals(-24.0, affine.determinant)
        assertEquals(-24.0, affine.linearDeterminant)
        assertEquals(12.0, projective.determinant)
        assertEquals(24.0, projective.linearDeterminant)
        assertEquals(-6.0, swapped.determinant)
        for (matrix in listOf(affine, projective, swapped)) {
            val inverse = assertNotNull(matrix.inverseOrNull())
            assertMatrix(Matrices4.Identity, matrix * inverse)
            assertMatrix(Matrices4.Identity, inverse * matrix)
            assertEquals(1.0 / matrix.determinant, inverse.determinant, 1e-12)
        }
        val point = Vectors.of(2.0, 3.0, 5.0)
        assertVector(point, affine.inverseTransformPosition(affine.transformPosition(point)))
        assertVector(point, affine.inverseTransformVector(affine.transformVector(point)))
    }

    @Test
    fun singularAndNonFiniteInversesHaveExplicitFailureModes() {
        val singular = Matrices4.of(
            1.0, 2.0, 3.0, 4.0,
            1.0, 2.0, 3.0, 4.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        )
        val invalid = listOf(Matrices4.Zero, singular, affine.withComponent(1, 1, Double.NaN), affine.withComponent(1, 1, Double.POSITIVE_INFINITY))
        for (matrix in invalid) {
            assertNull(matrix.inverseOrNull())
            assertFailsWith<IllegalArgumentException> { matrix.inverse() }
            assertSame(Matrices4.Identity, matrix.safeInverse())
            assertSame(projective, matrix.safeInverse(resultIfSingular = projective))
        }
        val small = Matrices4.fromScale(Vectors.of(0.125, 1.0, 1.0))
        assertNotNull(small.inverseOrNull(0.124))
        assertNull(small.inverseOrNull(0.125))
        assertNull(Matrices4.fromScale(Vectors.of(Double.MIN_VALUE, 1.0, 1.0)).inverseOrNull())
    }

    @Test
    fun transformedNormalsRemainPerpendicularUnderShearAndNonuniformScale() {
        val tangent = Vectors.of(1.0, 1.0, 0.0)
        val normal = Vectors.of(1.0, -1.0, 0.0)
        assertEquals(0.0, affine.transformVector(tangent).dot(affine.transformNormal(normal)), 1e-12)
        assertTrue(kotlin.math.abs(affine.transformVector(tangent).dot(affine.transformVector(normal))) > 1.0)
    }

    @Test
    fun namedAxesAndNormalizationRetainShearReflectionAndTheLastRow() {
        assertVector(2.0, 0.0, 0.0, affine.axisX)
        assertVector(1.0, 3.0, 0.0, affine.axisY)
        assertVector(0.0, 0.0, -4.0, affine.axisZ)
        assertVector(2.0, sqrt(10.0), 4.0, affine.axisLengths)
        assertVector(affine.axisX, affine.axis(0))
        assertVector(affine.axisY, affine.toBasis().up)
        val normalized = affine.withNormalizedAxes()
        assertVector(Vectors.One, normalized.axisLengths)
        assertTrue(normalized.axisX.dot(normalized.axisY) > 0.0)
        assertTrue(normalized.linearDeterminant < 0.0)
        assertVector(affine.translation, normalized.translation)
        assertVector(normalized.axisY, affine.unitAxis(1))
        assertContentEquals(projective.toRowMajorArray().takeLast(4), projective.withNormalizedAxes().toRowMajorArray().takeLast(4))
        val small = Matrices4.fromScale(Vectors.of(0.0, 1e-5, 0.125))
        assertVector(0.0, 0.0, 1.0, small.withNormalizedAxes().axisLengths)
        assertVector(0.0, 1.0, 1.0, small.withNormalizedAxes(0.0).axisLengths, 1e-12)
    }

    @Test
    fun affineAndRotationConversionsPreserveTheirGeometricMeaning() {
        assertMatrix(affine, affine.toAffine().toMatrix4())
        assertVector(affine.translation, affine.toAffine().translation)
        assertVector(Vectors.Zero, affine.withoutTranslation().translation)
        assertVector(Vectors.One, affine.withTranslation(Vectors.One).translation)
        assertVector(affine.axisY, affine.withTranslation(Vectors.One).axisY)
        val rotation = Quaternions.fromAxisAngleDegrees(Vectors.of(1.0, 2.0, 3.0).normalized(), 135.0)
        assertTrue(rotation.isSameRotation(rotation.toMatrix4().toQuaternion(), 1e-12))
        val transform = Transforms.of(Vectors.of(2.0, 3.0, 4.0), rotation, Vectors.of(-2.0, 3.0, 0.5))
        assertMatrix(transform.toMatrix4(), Matrices4.fromTRS(transform.translation, rotation, transform.scale))
        assertVector(transform.transformPosition(Vectors.One), transform.toMatrix4().transformPosition(Vectors.One))
    }
}
