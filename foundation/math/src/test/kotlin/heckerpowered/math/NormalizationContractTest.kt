/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.*

class NormalizationContractTest {
    @Test
    fun zeroSmallAndNonFiniteInputsHaveExplicitFailureAndFallbackPaths() {
        val vectorFallback = Vectors.UnitY
        val quaternionFallback = Quaternions.of(0.0, 1.0, 0.0, 0.0)
        val planeFallback = Planes.fromNormal(Vectors.UnitY, 3.0)

        for (component in listOf(0.0, 1.0E-6, Double.NaN, Double.POSITIVE_INFINITY)) {
            val vector = Vectors.of(component, 0.0, 0.0)
            val quaternion = Quaternions.of(component, 0.0, 0.0, 0.0)
            val plane = Planes.of(component, 0.0, 0.0, 0.0)

            assertNull(vector.normalizedOrNull())
            assertNull(vector.normalized2DOrNull())
            assertNull(quaternion.normalizedOrNull())
            assertNull(plane.normalizedOrNull())

            assertSame(vectorFallback, vector.normalizedOr(fallback = vectorFallback))
            assertSame(vectorFallback, vector.normalized2DOr(fallback = vectorFallback))
            assertSame(quaternionFallback, quaternion.normalizedOr(fallback = quaternionFallback))
            assertSame(planeFallback, plane.normalizedOr(fallback = planeFallback))

            assertFailsWith<IllegalArgumentException> { vector.normalized() }
            assertFailsWith<IllegalArgumentException> { vector.normalized2D() }
            assertFailsWith<IllegalArgumentException> { quaternion.normalized() }
            assertFailsWith<IllegalArgumentException> { plane.normalized() }
        }
    }

    @Test
    fun zeroToleranceStillRejectsZeroLength() {
        assertNull(Vectors.Zero.normalizedOrNull(0.0))
        assertNull(Vectors.Zero.normalized2DOrNull(0.0))
        assertNull(Quaternions.Zero.normalizedOrNull(0.0))
        assertNull(Planes.Zero.normalizedOrNull(0.0))
        assertFailsWith<IllegalArgumentException> { Vectors.Zero.normalized(0.0) }
        assertFailsWith<IllegalArgumentException> { Vectors.Zero.normalized2D(0.0) }
        assertFailsWith<IllegalArgumentException> { Quaternions.Zero.normalized(0.0) }
        assertFailsWith<IllegalArgumentException> { Planes.Zero.normalized(0.0) }
    }

    @Test
    fun thresholdIncludesEqualityAndAppliesToAlreadyUnitInputs() {
        for (length in listOf(0.5, 1.0)) {
            val tolerance = length * length
            val vector = Vectors.of(length, 0.0, 0.0)
            val quaternion = Quaternions.of(length, 0.0, 0.0, 0.0)
            val plane = Planes.of(length, 0.0, 0.0, 0.0)

            assertNull(vector.normalizedOrNull(tolerance))
            assertNull(vector.normalized2DOrNull(tolerance))
            assertNull(quaternion.normalizedOrNull(tolerance))
            assertNull(plane.normalizedOrNull(tolerance))
            assertFailsWith<IllegalArgumentException> { vector.normalized(tolerance) }
            assertFailsWith<IllegalArgumentException> { vector.normalized2D(tolerance) }
            assertFailsWith<IllegalArgumentException> { quaternion.normalized(tolerance) }
            assertFailsWith<IllegalArgumentException> { plane.normalized(tolerance) }

            assertEquals(1.0, vector.normalized(tolerance / 2.0).x)
            assertEquals(1.0, vector.normalized2D(tolerance / 2.0).x)
            assertEquals(1.0, quaternion.normalized(tolerance / 2.0).x)
            assertEquals(1.0, plane.normalized(tolerance / 2.0).x)
        }
    }

    @Test
    fun invalidToleranceIsAProgrammingErrorInEveryCheckedVariant() {
        val plane = Planes.fromNormal(Vectors.UnitX, 0.0)
        for (tolerance in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Vectors.UnitX.normalizedOrNull(tolerance) }
            assertFailsWith<IllegalArgumentException> { Vectors.UnitX.normalized2DOrNull(tolerance) }
            assertFailsWith<IllegalArgumentException> { Quaternions.Identity.normalizedOrNull(tolerance) }
            assertFailsWith<IllegalArgumentException> { plane.normalizedOrNull(tolerance) }
            assertFailsWith<IllegalArgumentException> { Vectors.UnitX.normalizedOr(Vectors.Zero, tolerance) }
            assertFailsWith<IllegalArgumentException> { Vectors.UnitX.normalized2DOr(Vectors.Zero, tolerance) }
            assertFailsWith<IllegalArgumentException> { Quaternions.Identity.normalizedOr(Quaternions.Zero, tolerance) }
            assertFailsWith<IllegalArgumentException> { plane.normalizedOr(Planes.Zero, tolerance) }
            assertFailsWith<IllegalArgumentException> { Vectors.UnitX.normalized(tolerance) }
            assertFailsWith<IllegalArgumentException> { Vectors.UnitX.normalized2D(tolerance) }
            assertFailsWith<IllegalArgumentException> { Quaternions.Identity.normalized(tolerance) }
            assertFailsWith<IllegalArgumentException> { plane.normalized(tolerance) }
        }
    }

    @Test
    fun checkedNormalizationAvoidsSquaredLengthOverflowAndUnderflow() {
        for (length in listOf(1.0E200, 1.0E-200)) {
            assertVector(1.0, 0.0, 0.0, Vectors.of(length, 0.0, 0.0).normalized(0.0))
            assertVector(1.0, 0.0, 0.0, Vectors.of(length, 0.0, 0.0).normalized2D(0.0))
            val quaternion = Quaternions.of(length, 0.0, 0.0, 0.0).normalized(0.0)
            assertEquals(listOf(1.0, 0.0, 0.0, 0.0), (0..3).map { quaternion[it] })
            val plane = Planes.of(length, 0.0, 0.0, 2.0 * length).normalized(0.0)
            assertEquals(1.0, plane.x)
            assertEquals(2.0, plane.w)
        }
    }

    @Test
    fun unrepresentableLengthsAndNormalizedOffsetsFailExplicitly() {
        val vector = Vectors.of(Double.MAX_VALUE, Double.MAX_VALUE, 0.0)
        val quaternion = Quaternions.of(Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.0)
        val plane = Planes.of(Double.MIN_VALUE, 0.0, 0.0, Double.MAX_VALUE)
        assertNull(vector.normalizedOrNull(0.0))
        assertNull(quaternion.normalizedOrNull(0.0))
        assertNull(plane.normalizedOrNull(0.0))
        assertFailsWith<IllegalArgumentException> { vector.normalized(0.0) }
        assertFailsWith<IllegalArgumentException> { quaternion.normalized(0.0) }
        assertFailsWith<IllegalArgumentException> { plane.normalized(0.0) }
    }

    @Test
    fun rawNormalizationStillExposesUndefinedArithmetic() {
        assertTrue(Vectors.Zero.normalizedUnsafe().containsNan())
        assertTrue(Vectors.Zero.normalized2DUnsafe().containsNan())
        assertTrue(Quaternions.Zero.normalizedUnsafe().containsNan())
        assertTrue(Planes.Zero.normalizedUnsafe().containsNaN())
    }

    @Test
    fun checkedVariantsSampleUnitInputsOnceAndReturnIndependentValues() {
        val vectorOperations: List<(VectorView) -> VectorView?> = listOf(
            { it.normalizedOrNull() }, { it.normalizedOr(Vectors.Zero) }, { it.normalized() },
        )
        for (operation in vectorOperations) {
            val reads = IntArray(3)
            val vector = object : VectorView {
                override val x: Double get() = if (++reads[0] == 1) 1.0 else Double.NaN
                override val y: Double get() = if (++reads[1] == 1) 0.0 else Double.NaN
                override val z: Double get() = if (++reads[2] == 1) 0.0 else Double.NaN
            }
            val normalized = assertNotNull(operation(vector))
            assertVector(1.0, 0.0, 0.0, normalized)
            assertVector(1.0, 0.0, 0.0, normalized)
            assertContentEquals(intArrayOf(1, 1, 1), reads)
        }

        val quaternionOperations: List<(QuaternionView) -> QuaternionView?> = listOf(
            { it.normalizedOrNull() }, { it.normalizedOr(Quaternions.Zero) }, { it.normalized() },
        )
        for (operation in quaternionOperations) {
            val reads = IntArray(4)
            val quaternion = object : QuaternionView {
                override val x: Double get() = if (++reads[0] == 1) 0.0 else Double.NaN
                override val y: Double get() = if (++reads[1] == 1) 0.0 else Double.NaN
                override val z: Double get() = if (++reads[2] == 1) 0.0 else Double.NaN
                override val w: Double get() = if (++reads[3] == 1) 1.0 else Double.NaN
            }
            val normalized = assertNotNull(operation(quaternion))
            repeat(2) { assertEquals(listOf(0.0, 0.0, 0.0, 1.0), (0..3).map { normalized[it] }) }
            assertContentEquals(intArrayOf(1, 1, 1, 1), reads)
        }
    }

    @Test
    fun horizontalNormalizationDoesNotSampleTheDiscardedVerticalComponent() {
        var xReads = 0
        var zReads = 0
        val vector = object : VectorView {
            override val x: Double get() = if (++xReads == 1) 3.0 else Double.NaN
            override val y: Double get() = error("Horizontal normalization must not read Y")
            override val z: Double get() = if (++zReads == 1) 4.0 else Double.NaN
        }
        assertVector(0.6, 0.0, 0.8, vector.normalized2D(), 1.0E-12)
        assertEquals(1, xReads)
        assertEquals(1, zReads)
    }
}
