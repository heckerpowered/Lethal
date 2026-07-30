/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VectorViewTest {
    @Test
    fun componentsExposeLengthsAndExtrema() {
        val vector = Geometry.vector(2.0, -3.0, 6.0)

        assertEquals(expected = 2.0, actual = vector[0])
        assertEquals(expected = -3.0, actual = vector[1])
        assertEquals(expected = 6.0, actual = vector[2])
        assertEquals(expected = 49.0, actual = vector.lengthSquared)
        assertEquals(expected = 7.0, actual = vector.length)
        assertEquals(expected = 40.0, actual = vector.horizontalLengthSquared)
        assertEquals(expected = sqrt(40.0), actual = vector.horizontalLength)
        assertEquals(expected = 6.0, actual = vector.maxComponent)
        assertEquals(expected = -3.0, actual = vector.minComponent)
        assertEquals(expected = 6.0, actual = vector.absMaxComponent)
        assertEquals(expected = 2.0, actual = vector.absMinComponent)
    }

    @Test
    fun invalidComponentIndexIsRejected() {
        val vector = Geometry.vector(1.0, 2.0, 3.0)

        assertFailsWith<IllegalArgumentException> { vector[-1] }
        assertFailsWith<IllegalArgumentException> { vector[3] }
    }

    @Test
    fun predicatesClassifyZeroNormalizedAndFiniteVectors() {
        assertTrue(Vectors.Zero.isZero())
        assertTrue(Geometry.vector(1.0E-7, -1.0E-7, 1.0E-7).isNearlyZero())
        assertFalse(Geometry.vector(2.0E-6, 0.0, 0.0).isNearlyZero())
        assertTrue(Geometry.vector(2.0E-6, 0.0, 0.0).isNearlyZero(epsilon = 2.0E-6))
        assertTrue(Vectors.UnitX.isExactlyNormalized())
        assertTrue(Geometry.vector(1.0000002, 0.0, 0.0).isNearlyNormalized())
        assertTrue(Geometry.vector(1.001, 0.0, 0.0).isNearlyNormalized(epsilon = 0.003))
        assertFalse(Geometry.vector(1.01, 0.0, 0.0).isNormalized())
        assertFalse(Geometry.vector(1.001, 0.0, 0.0).isNormalized(epsilon = 0.001))
        assertTrue(Geometry.vector(1.0, 2.0, 3.0).isFinite())
        assertFalse(Geometry.vector(Double.POSITIVE_INFINITY, 0.0, 0.0).isFinite())
        assertFalse(Geometry.vector(Double.NaN, 0.0, 0.0).isFinite())
        assertTrue(Geometry.vector(0.0, Double.NaN, 0.0).containsNan())
        assertFalse(Geometry.vector(1.0, 2.0, 3.0).containsNan())
        assertTrue(Geometry.vector(1.0, 1.00001, 0.99999).allComponentsEqual())
        assertFalse(Geometry.vector(1.0, 1.1, 1.0).allComponentsEqual())
        assertTrue(Geometry.vector(1.0, 1.1, 1.0).allComponentsEqual(tolerance = 0.101))
    }

    @Test
    fun arithmeticAndInterpolationOperateComponentWise() {
        val first = Geometry.vector(1.0, 2.0, 3.0)
        val second = Geometry.vector(4.0, -5.0, 6.0)

        assertVector(5.0, -3.0, 9.0, first + second)
        assertVector(-3.0, 7.0, -3.0, first - second)
        assertVector(-1.0, -2.0, -3.0, -first)
        assertVector(2.0, 4.0, 6.0, first * 2.0)
        assertVector(2.0, 4.0, 6.0, 2.0 * first)
        assertVector(0.5, 1.0, 1.5, first / 2.0)
        assertEquals(expected = 12.0, actual = first.dot(second))
        assertEquals(expected = 67.0, actual = first.distanceSquaredTo(second))
        assertEquals(expected = sqrt(67.0), actual = first.distanceTo(second))
        assertVector(27.0, 6.0, -13.0, first.cross(second))
        assertVector(1.75, 0.25, 3.75, first.interpolate(second, 0.25))
    }

    @Test
    fun normalizationPreservesDirectionAndUsesExplicitZeroFallbacks() {
        val vector = Geometry.vector(3.0, 4.0, 0.0)
        val vectorWithVerticalComponent = Geometry.vector(3.0, 7.0, 4.0)
        val fallback = Geometry.vector(9.0, 8.0, 7.0)

        assertVector(0.6, 0.8, 0.0, vector.unsafeNormal(), 1.0E-12)
        assertVector(0.6, 0.8, 0.0, vector.normalized(), 1.0E-12)
        assertVector(0.6, 0.0, 0.8, vectorWithVerticalComponent.unsafeNormal2D(), 1.0E-12)
        assertVector(0.6, 0.0, 0.8, vectorWithVerticalComponent.normalized2D(), 1.0E-12)
        assertSame(expected = fallback, actual = Vectors.Zero.safeNormal(resultIfZero = fallback))
        assertSame(expected = fallback, actual = Vectors.Zero.safeNormal2D(resultIfZero = fallback))
        assertVector(1.0, 0.0, 0.0, Vectors.UnitX.safeNormal())
        assertVector(1.0, 0.0, 0.0, Vectors.UnitX.safeNormal2D())

        val small = Geometry.vector(1.0E-5, 4.0, 0.0)
        assertSame(expected = fallback, actual = small.withY(0.0).safeNormal(tolerance = 1.0E-8, resultIfZero = fallback))
        assertVector(1.0, 0.0, 0.0, small.withY(0.0).safeNormal(tolerance = 1.0E-12, resultIfZero = fallback), 1.0E-12)
        assertSame(expected = fallback, actual = small.safeNormal2D(tolerance = 1.0E-8, resultIfZero = fallback))
        assertVector(1.0, 0.0, 0.0, small.safeNormal2D(tolerance = 1.0E-12, resultIfZero = fallback), 1.0E-12)
    }

    @Test
    fun reciprocalVariantsDistinguishUnsafeFallbackAndRequiredOperations() {
        val vector = Geometry.vector(2.0, -4.0, 0.5)

        assertVector(0.5, -0.25, 2.0, vector.reciprocal())
        assertVector(0.5, -0.25, 2.0, vector.requireReciprocal())
        assertVector(0.5, -0.25, 2.0, requireNotNull(vector.reciprocalOrNull()))

        val fallback = Geometry.vector(10.0, 20.0, 30.0)
        val unsafe = Geometry.vector(0.0, -0.0, Double.MIN_VALUE)
        assertVector(10.0, -20.0, 30.0, unsafe.safeReciprocal(fallback))
        assertVector(1.0E30, -1.0E30, 1.0E30, unsafe.safeReciprocal())
        assertNull(unsafe.reciprocalOrNull())
        assertFailsWith<IllegalArgumentException> { unsafe.requireReciprocal() }
    }

    @Test
    fun requiredAndNullableReciprocalsReadEachComponentOnce() {
        val sample = Geometry.vector(2.0, -4.0, 0.5)
        val requiredInput = SequencedVectorView(sample, sample)
        val nullableInput = SequencedVectorView(sample, sample)

        assertVector(0.5, -0.25, 2.0, requiredInput.requireReciprocal())
        assertEquals(expected = Triple(1, 1, 1), actual = requiredInput.readCounts)

        assertVector(0.5, -0.25, 2.0, requireNotNull(nullableInput.reciprocalOrNull()))
        assertEquals(expected = Triple(1, 1, 1), actual = nullableInput.readCounts)
    }

    @Test
    fun requiredAndNullableReciprocalsUseTheFirstSampleWhenLaterReadsChange() {
        val firstSample = Geometry.vector(2.0, -4.0, 0.5)
        val laterSample = Geometry.vector(8.0, -16.0, 2.0)
        val requiredInput = SequencedVectorView(firstSample, laterSample)
        val nullableInput = SequencedVectorView(firstSample, laterSample)

        val requiredResult = requiredInput.requireReciprocal()
        val nullableResult = requireNotNull(nullableInput.reciprocalOrNull())
        val laterSampleResult = laterSample.reciprocal()

        assertVector(0.5, -0.25, 2.0, requiredResult)
        assertVector(0.5, -0.25, 2.0, nullableResult)
        assertNotEquals(illegal = laterSampleResult.coordinates(), actual = requiredResult.coordinates())
        assertNotEquals(illegal = laterSampleResult.coordinates(), actual = nullableResult.coordinates())
    }

    @Test
    fun componentTransformsRetainUnchangedAxes() {
        val vector = Geometry.vector(-1.4, 2.6, -3.2)

        assertVector(1.4, 2.6, 3.2, vector.abs())
        assertVector(-2.0, 2.6, -4.0, vector.componentMin(Geometry.vector(-2.0, 4.0, -4.0)))
        assertVector(0.0, 4.0, -3.2, vector.componentMax(Geometry.vector(0.0, 4.0, -4.0)))
        assertVector(8.0, 2.6, -3.2, vector.withX(8.0))
        assertVector(-1.4, 8.0, -3.2, vector.withY(8.0))
        assertVector(-1.4, 2.6, 8.0, vector.withZ(8.0))
        assertVector(-2.0, 2.0, -4.0, vector.floor())
        assertVector(-1.0, 3.0, -3.0, vector.ceil())
        assertVector(-1.0, 3.0, -3.0, vector.round())
        assertVector(-1.0, 2.0, -2.0, vector.coerceIn(Geometry.vector(-1.0, 0.0, -2.0), Geometry.vector(1.0, 2.0, 2.0)))
        assertVector(-1.4, 2.0, -2.0, vector.coerceComponentsIn(-2.0, 2.0))
        assertVector(-1.4, 2.0, -2.0, vector.coerceComponentsIn(2.0))
    }

    @Test
    fun lengthCoercionOnlyScalesWhenOutsideTheRequestedRange() {
        val vector = Geometry.vector(3.0, 4.0, 0.0)
        val fallback = Geometry.vector(6.0, 0.0, 0.0)

        assertVector(3.6, 4.8, 0.0, vector.coerceLengthIn(6.0, 8.0), 1.0E-12)
        assertVector(2.4, 3.2, 0.0, vector.coerceLengthIn(2.0, 4.0), 1.0E-12)
        assertVector(3.0, 4.0, 0.0, vector.coerceLengthIn(4.0, 6.0))
        assertVector(0.0, 0.0, 0.0, Vectors.Zero.coerceLengthIn(2.0, 4.0))
        assertVector(1.2, 1.6, 0.0, vector.coerceLengthAtMost(2.0), 1.0E-12)
        assertVector(3.6, 4.8, 0.0, vector.coerceLengthAtLeast(6.0), 1.0E-12)
        assertVector(3.0, 4.0, 0.0, vector.coerceLengthAtMost(5.0))
        assertVector(3.0, 4.0, 0.0, vector.coerceLengthAtLeast(5.0))
        assertVector(3.0, 4.0, 0.0, vector.coerceLengthAtLeast(0.0))
        assertSame(expected = fallback, actual = Vectors.Zero.coerceLengthAtLeast(6.0, fallback))
        assertVector(0.0, 0.0, 0.0, vector.coerceLengthAtMost(0.0))
    }

    @Test
    fun horizontalLengthCoercionPreservesVerticalComponent() {
        val vector = Geometry.vector(3.0, 7.0, 4.0)
        val fallback = Geometry.vector(2.0, 9.0, 3.0)

        assertVector(3.6, 7.0, 4.8, vector.coerceHorizontalLengthIn(6.0, 8.0), 1.0E-12)
        assertVector(2.4, 7.0, 3.2, vector.coerceHorizontalLengthIn(2.0, 4.0), 1.0E-12)
        assertVector(3.0, 7.0, 4.0, vector.coerceHorizontalLengthIn(4.0, 6.0))
        assertVector(0.0, 7.0, 0.0, Geometry.vector(0.0, 7.0, 0.0).coerceHorizontalLengthIn(2.0, 4.0))
        assertVector(1.2, 7.0, 1.6, vector.coerceHorizontalLengthAtMost(2.0), 1.0E-12)
        assertVector(3.6, 7.0, 4.8, vector.coerceHorizontalLengthAtLeast(6.0), 1.0E-12)
        assertVector(3.0, 7.0, 4.0, vector.coerceHorizontalLengthAtMost(5.0))
        assertVector(3.0, 7.0, 4.0, vector.coerceHorizontalLengthAtLeast(5.0))
        assertVector(3.0, 7.0, 4.0, vector.coerceHorizontalLengthAtLeast(0.0))
        assertVector(2.0, 7.0, 3.0, Geometry.vector(0.0, 7.0, 0.0).coerceHorizontalLengthAtLeast(6.0, fallback))
        assertVector(0.0, 7.0, 0.0, vector.coerceHorizontalLengthAtMost(0.0))
    }

    @Test
    fun projectionRotationAndMirroringUseTheGivenAxes() {
        val vector = Geometry.vector(2.0, 3.0, 4.0)

        assertVector(2.0, 0.0, 0.0, vector.projectOnto(Geometry.vector(2.0, 0.0, 0.0)))
        assertVector(0.0, 3.0, 0.0, vector.projectOntoNormal(Vectors.UnitY))
        assertVector(2.0, 0.0, 4.0, vector.projectOntoPlane(Vectors.UnitY))
        assertVector(0.0, 0.0, -1.0, Vectors.UnitX.rotateAroundAxisRadians(PI * 0.5, Vectors.UnitY), 1.0E-12)
        assertVector(0.0, 0.0, -1.0, Vectors.UnitX.rotateAngleAxis(90.0, Vectors.UnitY), 1.0E-12)
        assertVector(1.0, 2.0, 3.0, Geometry.vector(1.0, -2.0, 3.0).mirrorByVector(Vectors.UnitY))
    }

    @Test
    fun snappingSignsAndHeadingExposeSpatialIntent() {
        val vector = Geometry.vector(1.24, -1.26, 2.75)

        assertVector(1.0, -1.5, 3.0, vector.gridSnap(0.5))
        assertVector(1.24, -1.26, 2.75, vector.gridSnap(0.0))
        assertVector(1.0, -1.0, 1.0, vector.sign())
        assertEquals(expected = 0.0, actual = Vectors.UnitX.horizontalHeadingRadians())
        assertEquals(expected = PI * 0.5, actual = Vectors.UnitZ.horizontalHeadingRadians())
        assertEquals(expected = PI, actual = Vectors.NegativeUnitX.horizontalHeadingRadians())
    }

    @Test
    fun vectorAndRotatorConversionsRoundTripMinecraftViewDirections() {
        val rotator = Geometry.rotator(20.0, -35.0)

        val roundTrip = rotator.toViewVector().toRotator()

        assertEquals(expected = rotator.pitch, actual = roundTrip.pitch, absoluteTolerance = 1.0E-12)
        assertEquals(expected = rotator.yaw, actual = roundTrip.yaw, absoluteTolerance = 1.0E-12)
        assertEquals(expected = 0.0, actual = roundTrip.roll)
    }

    @Test
    fun vectorCollectionsExposeCanonicalDirectionsAndOperations() {
        assertVector(0.0, 0.0, 0.0, Vectors.Zero)
        assertVector(1.0, 1.0, 1.0, Vectors.One)
        assertVector(1.0, 0.0, 0.0, Vectors.UnitX)
        assertVector(0.0, 1.0, 0.0, Vectors.UnitY)
        assertVector(0.0, 0.0, 1.0, Vectors.UnitZ)
        assertVector(-1.0, 0.0, 0.0, Vectors.NegativeUnitX)
        assertVector(0.0, -1.0, 0.0, Vectors.NegativeUnitY)
        assertVector(0.0, 0.0, -1.0, Vectors.NegativeUnitZ)
        assertVector(2.0, 3.0, 4.0, Vectors.of(2.0, 3.0, 4.0))
        assertVector(-1.0, -1.0, -1.0, Vectors.min(Geometry.vector(-1.0, 2.0, 4.0), Geometry.vector(3.0, -1.0, -1.0)))
        assertVector(3.0, 2.0, 4.0, Vectors.max(Geometry.vector(-1.0, 2.0, 4.0), Geometry.vector(3.0, -1.0, -1.0)))
        assertEquals(expected = 25.0, actual = Vectors.distanceSquared(Vectors.Zero, Geometry.vector(3.0, 4.0, 0.0)))
        assertEquals(expected = 5.0, actual = Vectors.distance(Vectors.Zero, Geometry.vector(3.0, 4.0, 0.0)))
        assertEquals(expected = 0.0, actual = Vectors.dot(Vectors.UnitX, Vectors.UnitY))
        assertVector(0.0, 0.0, 1.0, Vectors.cross(Vectors.UnitX, Vectors.UnitY))
        assertVector(0.25, 0.25, 0.25, Vectors.lerp(Vectors.Zero, Vectors.One, 0.25))
    }

    @Test
    fun minecraftDirectionsAndPointBoxesUseMinecraftCoordinates() {
        assertVector(0.0, 1.0, 0.0, MinecraftDirections.Up)
        assertVector(0.0, -1.0, 0.0, MinecraftDirections.Down)
        assertVector(0.0, 0.0, 1.0, MinecraftDirections.Forward)
        assertVector(0.0, 0.0, -1.0, MinecraftDirections.Backward)
        assertVector(-1.0, 0.0, 0.0, MinecraftDirections.Right)
        assertVector(1.0, 0.0, 0.0, MinecraftDirections.Left)

        val point = Geometry.vector(1.0, 2.0, 3.0)
        assertBox(1.0, 2.0, 3.0, 1.0, 2.0, 3.0, point.asPointBox())
    }

    private class SequencedVectorView(private val firstSample: VectorView, private val laterSample: VectorView) : VectorView {
        private var xReadCount = 0
        private var yReadCount = 0
        private var zReadCount = 0

        override val x: Double
            get() {
                xReadCount++
                return sample(xReadCount).x
            }
        override val y: Double
            get() {
                yReadCount++
                return sample(yReadCount).y
            }
        override val z: Double
            get() {
                zReadCount++
                return sample(zReadCount).z
            }

        val readCounts: Triple<Int, Int, Int>
            get() = Triple(xReadCount, yReadCount, zReadCount)

        private fun sample(readCount: Int): VectorView = if (readCount == 1) firstSample else laterSample
    }
}
