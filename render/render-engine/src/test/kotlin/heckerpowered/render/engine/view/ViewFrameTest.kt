/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.*

import kotlin.test.*

class ViewFrameTest {
    @Test
    fun originBecomesZeroAndBasisDisplacementsBecomeViewCoordinates() {
        val origin = Vectors.of(10.0, 20.0, 30.0)
        val frame = ViewFrame.of(origin, Vectors.UnitZ, Vectors.UnitY, Vectors.NegativeUnitX)

        assertVector(0.0, 0.0, 0.0, frame.worldToView.transformPosition(origin))
        assertVector(1.0, 0.0, 0.0, frame.worldToView.transformVector(frame.right))
        assertVector(0.0, 1.0, 0.0, frame.worldToView.transformVector(frame.up))
        assertVector(0.0, 0.0, 1.0, frame.worldToView.transformVector(frame.forward))
        val point = origin + frame.right * 2.0 + frame.up * 3.0 + frame.forward * 4.0
        assertVector(2.0, 3.0, 4.0, frame.worldToView.transformPosition(point))
    }

    @Test
    fun constructionRejectsNonUnitNonOrthogonalAndLeftHandedAxes() {
        val invalidBases = listOf(
            listOf(Vectors.UnitX * 2.0, Vectors.UnitY, Vectors.UnitZ),
            listOf(Vectors.UnitX, Vectors.UnitY * 2.0, Vectors.UnitZ),
            listOf(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ * 2.0),
            listOf(Vectors.Zero, Vectors.UnitY, Vectors.UnitZ),
            listOf(Vectors.UnitX, Vectors.of(0.6, 0.8, 0.0), Vectors.UnitZ),
            listOf(Vectors.UnitX, Vectors.UnitY, Vectors.of(0.6, 0.0, 0.8)),
            listOf(Vectors.UnitX, Vectors.UnitY, Vectors.of(0.0, 0.6, 0.8)),
            listOf(Vectors.UnitX, Vectors.UnitY, Vectors.NegativeUnitZ),
        )
        for (basis in invalidBases) {
            assertFailsWith<IllegalArgumentException> { ViewFrame.of(Vectors.Zero, basis[0], basis[1], basis[2]) }
        }
        assertFailsWith<IllegalArgumentException> {
            ViewFrame.of(Vectors.Zero, Vectors.UnitX, Vectors.UnitY, Vectors.NegativeUnitZ, 3.0)
        }
    }

    @Test
    fun constructionRejectsNonFiniteOriginAxesAndInvalidTolerance() {
        for (component in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val invalid = Vectors.of(component, 0.0, 0.0)
            assertFailsWith<IllegalArgumentException> { ViewFrame.of(invalid, Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ) }
            assertFailsWith<IllegalArgumentException> { ViewFrame.of(Vectors.Zero, invalid, Vectors.UnitY, Vectors.UnitZ) }
            assertFailsWith<IllegalArgumentException> { ViewFrame.of(Vectors.Zero, Vectors.UnitX, invalid, Vectors.UnitZ) }
            assertFailsWith<IllegalArgumentException> { ViewFrame.of(Vectors.Zero, Vectors.UnitX, Vectors.UnitY, invalid) }
        }
        for (epsilon in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ViewFrame.of(Vectors.Zero, Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ, epsilon) }
        }
    }

    @Test
    fun toleranceAcceptsSmallBasisErrorsWithoutSilentlyRepairingInputs() {
        val right = Vectors.of(1.0 + 1.0E-8, 0.0, 0.0)
        val frame = ViewFrame.of(Vectors.Zero, right, Vectors.UnitY, Vectors.UnitZ)
        assertEquals(right.x, frame.right.x)
        assertFailsWith<IllegalArgumentException> { ViewFrame.of(Vectors.Zero, right, Vectors.UnitY, Vectors.UnitZ, 0.0) }
        ViewFrame.of(Vectors.Zero, Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ, 0.0)
    }

    @Test
    fun validationAndConstructionUseTheSameIndependentSamples() {
        val origin = SampleOnceVector(10.0, 20.0, 30.0)
        val right = SampleOnceVector(1.0, 0.0, 0.0)
        val up = SampleOnceVector(0.0, 1.0, 0.0)
        val forward = SampleOnceVector(0.0, 0.0, 1.0)
        val frame = ViewFrame.of(origin, right, up, forward)

        repeat(2) {
            assertVector(10.0, 20.0, 30.0, frame.origin)
            assertVector(1.0, 0.0, 0.0, frame.right)
            assertVector(0.0, 1.0, 0.0, frame.up)
            assertVector(0.0, 0.0, 1.0, frame.forward)
            assertVector(1.0, 2.0, 3.0, frame.worldToView.transformPosition(Vectors.of(11.0, 22.0, 33.0)))
        }
        for (vector in listOf(origin, right, up, forward)) assertContentEquals(intArrayOf(1, 1, 1), vector.reads)
    }

    @Test
    fun viewCombinesTheValidatedFrameWithPositiveForwardDepth() {
        val frame = ViewFrame.of(Vectors.of(10.0, 20.0, 30.0), Vectors.UnitZ, Vectors.UnitY, Vectors.NegativeUnitX)
        val view = View(frame, PerspectiveProjection.degrees(90.0, 1.0, 1.0, 10.0))
        val frustum = Frustum.fromWorldToClip(view.worldToClip)
        assertSame(frame.worldToView, view.worldToView)
        assertTrue(frustum.contains(frame.origin + frame.forward * 5.0))
        assertFalse(frustum.contains(frame.origin - frame.forward * 5.0))
        assertFalse(frustum.contains(frame.origin + frame.forward * 0.5))
        assertFalse(frustum.contains(frame.origin + frame.forward * 11.0))
    }

    private fun assertVector(x: Double, y: Double, z: Double, actual: VectorView) {
        assertEquals(x, actual.x, 1.0E-12, "x")
        assertEquals(y, actual.y, 1.0E-12, "y")
        assertEquals(z, actual.z, 1.0E-12, "z")
    }

    private class SampleOnceVector(
        private val firstX: Double,
        private val firstY: Double,
        private val firstZ: Double
    ) : VectorView {
        val reads = IntArray(3)
        override val x: Double get() = if (++reads[0] == 1) firstX else Double.NaN
        override val y: Double get() = if (++reads[1] == 1) firstY else Double.NaN
        override val z: Double get() = if (++reads[2] == 1) firstZ else Double.NaN
    }
}
