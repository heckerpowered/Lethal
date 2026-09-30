/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.*

class QuaternionViewTest {
    @Test
    fun componentsAndPredicatesIncludeTheRealComponent() {
        val quaternion = Quaternions.of(1.0, -2.0, 3.0, -4.0)
        assertEquals(listOf(1.0, -2.0, 3.0, -4.0), (0..3).map { quaternion[it] })
        assertEquals(30.0, quaternion.lengthSquared)
        assertEquals(sqrt(30.0), quaternion.length)
        assertEquals(30.0, quaternion.dot(quaternion))
        assertFailsWith<IllegalArgumentException> { quaternion[-1] }
        assertFailsWith<IllegalArgumentException> { quaternion[4] }
        assertTrue(Quaternions.Zero.isZero())
        assertFalse(Quaternions.Identity.isZero())
        assertTrue(Quaternions.of(1e-7, -1e-7, 0.0, 1e-7).isNearlyZero())
        assertFalse(Quaternions.Identity.isNearlyZero())
        assertTrue(Quaternions.Identity.isExactlyNormalized())
        assertTrue(Quaternions.Identity.withW(1.0 + 1e-7).isNearlyNormalized())
        assertFalse(quaternion.isNormalized())
        assertTrue(quaternion.isFinite())
        assertFalse(quaternion.withW(Double.POSITIVE_INFINITY).isFinite())
        assertFalse(quaternion.withW(Double.POSITIVE_INFINITY).containsNan())
        assertTrue(quaternion.withW(Double.NaN).containsNan())
    }

    @Test
    fun arithmeticAndComponentCopiesDoNotNormalizeOrModifyTheSource() {
        val first = Quaternions.of(1.0, 2.0, 3.0, 4.0)
        val second = Quaternions.of(4.0, -3.0, 2.0, -1.0)
        assertQuaternion(Quaternions.of(5.0, -1.0, 5.0, 3.0), first + second)
        assertQuaternion(Quaternions.of(-3.0, 5.0, 1.0, 5.0), first - second)
        assertQuaternion(Quaternions.of(-1.0, -2.0, -3.0, -4.0), -first)
        assertQuaternion(Quaternions.of(2.0, 4.0, 6.0, 8.0), first * 2.0)
        assertQuaternion(first * 2.0, 2.0 * first)
        assertQuaternion(first, (first * 2.0) / 2.0)
        assertQuaternion(Quaternions.of(5.0, 6.0, 7.0, 8.0), first.withX(5.0).withY(6.0).withZ(7.0).withW(8.0))
        assertQuaternion(Quaternions.of(1.0, 2.0, 3.0, 4.0), first)
    }

    @Test
    fun rotationEqualityRecognizesOppositeRepresentationsWithoutChangingComponentEquality() {
        val rotation = Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 70.0)
        assertFalse(rotation.isNearlyEqual(-rotation))
        assertTrue(rotation.isSameRotation(-rotation))
        assertTrue((-Quaternions.Identity).isIdentity())
        assertFalse(Quaternions.Zero.isIdentity())
        assertTrue(rotation.isNearlyEqual(rotation.withW(rotation.w + 1e-7)))
        assertFalse(rotation.isNearlyEqual(rotation.withW(rotation.w + 1e-3)))
    }

    @Test
    fun normalizationHandlesZeroAndExplicitSquaredLengthThresholds() {
        val quaternion = Quaternions.of(0.0, 3.0, 0.0, 4.0)
        val expected = Quaternions.of(0.0, 0.6, 0.0, 0.8)
        assertQuaternion(expected, quaternion.normalizedUnsafe())
        assertQuaternion(expected, quaternion.normalized())
        assertQuaternion(Quaternions.Identity, Quaternions.Identity.normalizedOr(Quaternions.Zero))
        assertFailsWith<IllegalArgumentException> { Quaternions.Zero.normalized() }
        assertSame(Quaternions.Identity, Quaternions.Zero.normalizedOr(Quaternions.Identity, tolerance = 0.0))
        val small = quaternion * 1e-6
        assertSame(quaternion, small.normalizedOr(fallback = quaternion))
        assertQuaternion(expected, small.normalizedOr(Quaternions.Identity, tolerance = 1e-12))
        assertTrue(Quaternions.Zero.normalizedUnsafe().containsNan())
        assertFailsWith<IllegalArgumentException> { quaternion.normalizedOr(Quaternions.Identity, tolerance = -1.0) }
        assertFailsWith<IllegalArgumentException> { quaternion.normalizedOr(Quaternions.Identity, tolerance = Double.NaN) }
    }

    @Test
    fun inverseIsAlgebraicForNonUnitQuaternionsAndRejectsUndefinedInputs() {
        val quaternion = Quaternions.of(1.0, -2.0, 3.0, 4.0)
        assertQuaternion(Quaternions.Identity, quaternion * quaternion.inverse())
        assertQuaternion(Quaternions.Identity, quaternion.inverse() * quaternion)
        assertQuaternion(quaternion.inverse(), quaternion.requireInverse())
        assertQuaternion(quaternion.inverse(), assertNotNull(quaternion.inverseOrNull()))
        assertQuaternion(quaternion, quaternion.conjugate().conjugate())
        assertNull(Quaternions.Zero.inverseOrNull())
        assertNull(quaternion.withW(Double.NaN).inverseOrNull())
        assertNull(quaternion.withX(Double.POSITIVE_INFINITY).inverseOrNull())
        assertFailsWith<IllegalArgumentException> { Quaternions.Zero.requireInverse() }
        assertTrue(Quaternions.Zero.inverse().containsNan())
    }

    @Test
    fun checkedInverseUsesOneSamplePerComponent() {
        val reads = IntArray(4)
        val quaternion = object : QuaternionView {
            override val x: Double get() = if (++reads[0] == 1) 1.0 else 0.0
            override val y: Double get() = if (++reads[1] == 1) 2.0 else 0.0
            override val z: Double get() = if (++reads[2] == 1) 3.0 else 0.0
            override val w: Double get() = if (++reads[3] == 1) 4.0 else 0.0
        }
        assertQuaternion(Quaternions.of(-1.0 / 30.0, -2.0 / 30.0, -3.0 / 30.0, 4.0 / 30.0), quaternion.requireInverse())
        assertContentEquals(intArrayOf(1, 1, 1, 1), reads)
    }

    @Test
    fun axisAngleRotationsFollowVectorConventionsAndPreserveLengths() {
        val vector = Vectors.of(2.0, -3.0, 5.0)
        val axes = listOf(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ, Vectors.of(1.0, 2.0, 3.0).normalized())
        for (axis in axes) {
            for (angle in listOf(-2.0, 0.0, 0.7, PI)) {
                val rotation = Quaternions.fromAxisAngleRadians(axis, angle)
                assertVector(vector.rotateAroundAxisRadians(angle, axis), rotation.rotateVector(vector))
                assertVector(vector, rotation.unrotateVector(rotation * vector))
                assertEquals(vector.length, (rotation * vector).length, 1e-12)
                assertQuaternion(rotation, Quaternions.fromAxisAngleDegrees(axis, Math.toDegrees(angle)))
            }
        }
        assertVector(Vectors.NegativeUnitZ, Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 90.0) * Vectors.UnitX)
    }

    @Test
    fun multiplicationAppliesTheRightOperandFirst() {
        val first = Quaternions.fromAxisAngleDegrees(Vectors.UnitX, 90.0)
        val second = Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 90.0)
        val vector = Vectors.UnitZ
        assertVector(first * (second * vector), (first * second) * vector)
        assertVector(Vectors.UnitX, (first * second) * vector)
        assertVector(Vectors.NegativeUnitY, (second * first) * vector)
    }

    @Test
    fun basisRoundTripsIncludingHalfTurnsAboutEveryAxis() {
        val axes = listOf(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ, Vectors.of(2.0, -3.0, 4.0).normalized())
        for (axis in axes) {
            for (angle in listOf(0.0, 0.4, 2.0, PI)) {
                val rotation = Quaternions.fromAxisAngleRadians(axis, angle)
                val basis = rotation.toBasis()
                assertVector(rotation * Vectors.UnitX, basis.right)
                assertVector(rotation * Vectors.UnitY, basis.up)
                assertVector(rotation * Vectors.UnitZ, basis.forward)
                assertVector(basis.forward, basis.right.cross(basis.up))
                assertTrue(rotation.isSameRotation(Quaternions.fromBasis(basis), 1e-12))
            }
        }
    }

    @Test
    fun fromToHandlesScaledParallelOppositeAndNearlyOppositeDirections() {
        val start = Vectors.of(1.0, 2.0, -3.0)
        val targets = listOf(start * 4.0, -start, Vectors.of(2.0, -1.0, 5.0), -start + Vectors.UnitX * 1e-7)
        for (target in targets) {
            val rotation = Quaternions.fromTo(start, target)
            assertTrue(rotation.isNormalized())
            assertVector(target.normalized(), rotation * start.normalized())
        }
        for (axis in listOf(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ)) {
            assertVector(-axis, Quaternions.fromTo(axis, -axis) * axis)
        }
        assertSame(Quaternions.Identity, Quaternions.fromTo(Vectors.Zero, start))
        assertSame(Quaternions.Identity, Quaternions.fromTo(start, Vectors.Zero))
    }

    @Test
    fun anglesAndAxesUseTheShortestEquivalentRotation() {
        val rotation = Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 270.0)
        assertEquals(90.0, rotation.angleDegrees(), 1e-12)
        assertVector(Vectors.NegativeUnitY, rotation.rotationAxis())
        assertTrue(rotation.isSameRotation(Quaternions.fromAxisAngleRadians(rotation.rotationAxis(), rotation.angleRadians())))
        assertEquals(rotation.angleRadians(), (-rotation).angleRadians(), 1e-12)
        assertVector(rotation.rotationAxis(), (-rotation).rotationAxis())
        assertEquals(90.0, rotation.angularDistanceDegrees(Quaternions.Identity), 1e-12)
        assertEquals(0.0, rotation.angularDistanceRadians(-rotation), 1e-12)
        assertSame(Vectors.UnitX, Quaternions.Identity.rotationAxis())
        assertSame(Vectors.UnitZ, Quaternions.Identity.rotationAxis(Vectors.UnitZ))
    }

    @Test
    fun interpolationDistinguishesComponentBlendingFromShortestPathRotation() {
        val start = Quaternions.Identity
        val target = Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 120.0)
        assertFalse(start.lerp(target, 0.5).isNormalized())
        assertTrue(start.normalizedInterpolate(target, 0.5).isNormalized())
        for (alpha in listOf(-0.5, 0.0, 0.25, 0.5, 1.0, 1.5)) {
            val expected = Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 120.0 * alpha)
            assertTrue(expected.isSameRotation(start.sphericalInterpolate(target, alpha), 1e-12))
            assertTrue(expected.isSameRotation(start.interpolate(-target, alpha), 1e-12))
        }
        assertQuaternion(Quaternions.Zero, target.lerp(-target, 0.5))
        assertTrue(target.isSameRotation(target.sphericalInterpolate(-target, 0.5), 1e-12))
        assertTrue(target.isSameRotation(target.normalizedInterpolate(-target, 0.5), 1e-12))
        val close = Quaternions.fromAxisAngleDegrees(Vectors.UnitY, 0.001)
        assertTrue(start.sphericalInterpolate(close, 0.5).isFinite())
        assertEquals(0.0005, start.sphericalInterpolate(close, 0.5).angleDegrees(), 1e-8)
    }

    @Test
    fun collectionOperationsMatchTheirInstanceCounterparts() {
        val start = Quaternions.Identity
        val target = Quaternions.fromAxisAngleDegrees(Vectors.UnitZ, 90.0)
        assertEquals(start.dot(target), Quaternions.dot(start, target))
        assertQuaternion(start.lerp(target, 0.3), Quaternions.lerp(start, target, 0.3))
        assertQuaternion(start.normalizedInterpolate(target, 0.3), Quaternions.normalizedInterpolate(start, target, 0.3))
        assertQuaternion(start.sphericalInterpolate(target, 0.3), Quaternions.sphericalInterpolate(start, target, 0.3))
    }

    private fun assertQuaternion(expected: QuaternionView, actual: QuaternionView) {
        for (index in 0..3) assertEquals(expected[index], actual[index], 1e-12, "Component $index")
    }

    private fun assertVector(expected: VectorView, actual: VectorView) {
        for (index in 0..2) assertEquals(expected[index], actual[index], 1e-12, "Component $index")
    }
}
