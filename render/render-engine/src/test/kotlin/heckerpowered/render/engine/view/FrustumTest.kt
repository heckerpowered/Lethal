/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.Box
import heckerpowered.math.*

import kotlin.test.*

class FrustumTest {
    @Test
    fun identityMatrixExtractsInwardUnitPlanesForZeroToOneDepth() {
        val frustum = Frustum.fromWorldToClip(Matrices.Identity)

        assertPlane(1.0, 0.0, 0.0, -1.0, frustum.left)
        assertPlane(-1.0, 0.0, 0.0, -1.0, frustum.right)
        assertPlane(0.0, 1.0, 0.0, -1.0, frustum.top)
        assertPlane(0.0, -1.0, 0.0, -1.0, frustum.bottom)
        assertPlane(0.0, 0.0, 1.0, 0.0, frustum.near)
        assertPlane(0.0, 0.0, -1.0, -1.0, frustum.far)
    }

    @Test
    fun containmentIncludesEveryBoundaryAndRejectsPointsBeyondEachPlane() {
        val frustum = Frustum.fromWorldToClip(Matrices.Identity)
        assertTrue(frustum.contains(Vectors.of(0.0, 0.0, 0.5)))
        val boundaryPoints = listOf(
            Vectors.of(-1.0, 0.0, 0.5),
            Vectors.of(1.0, 0.0, 0.5),
            Vectors.of(0.0, -1.0, 0.5),
            Vectors.of(0.0, 1.0, 0.5),
            Vectors.of(0.0, 0.0, 0.0),
            Vectors.of(0.0, 0.0, 1.0),
        )
        val outsidePoints = listOf(
            Vectors.of(-1.01, 0.0, 0.5),
            Vectors.of(1.01, 0.0, 0.5),
            Vectors.of(0.0, -1.01, 0.5),
            Vectors.of(0.0, 1.01, 0.5),
            Vectors.of(0.0, 0.0, -0.01),
            Vectors.of(0.0, 0.0, 1.01),
        )
        for (point in boundaryPoints) assertTrue(frustum.contains(point), "$point")
        for (point in outsidePoints) assertFalse(frustum.contains(point), "$point")
    }

    @Test
    fun boxIntersectionRejectsBoxesCompletelyBeyondEachPlane() {
        val frustum = Frustum.fromWorldToClip(Matrices.Identity)
        val outsideBoxes = listOf(
            Box(-3.0, -0.5, 0.2, -2.0, 0.5, 0.8),
            Box(2.0, -0.5, 0.2, 3.0, 0.5, 0.8),
            Box(-0.5, -3.0, 0.2, 0.5, -2.0, 0.8),
            Box(-0.5, 2.0, 0.2, 0.5, 3.0, 0.8),
            Box(-0.5, -0.5, -2.0, 0.5, 0.5, -1.0),
            Box(-0.5, -0.5, 2.0, 0.5, 0.5, 3.0),
        )
        for (box in outsideBoxes) assertFalse(frustum.intersects(box), "$box")
    }

    @Test
    fun boxIntersectionIncludesTouchingOverlappingAndEnclosingBoxes() {
        val frustum = Frustum.fromWorldToClip(Matrices.Identity)
        val intersectingBoxes = listOf(
            Box(-2.0, -0.5, 0.2, -1.0, 0.5, 0.8),
            Box(1.0, -0.5, 0.2, 2.0, 0.5, 0.8),
            Box(-0.5, -2.0, 0.2, 0.5, -1.0, 0.8),
            Box(-0.5, 1.0, 0.2, 0.5, 2.0, 0.8),
            Box(-0.5, -0.5, -1.0, 0.5, 0.5, 0.0),
            Box(-0.5, -0.5, 1.0, 0.5, 0.5, 2.0),
            Box(-0.5, -0.5, 0.2, 0.5, 0.5, 0.8),
            Box(0.5, 0.5, 0.5, 2.0, 2.0, 2.0),
            Box(-2.0, -2.0, -1.0, 2.0, 2.0, 2.0),
        )
        for (box in intersectingBoxes) assertTrue(frustum.intersects(box), "$box")
    }

    @Test
    fun pointBoxesAgreeWithPointContainment() {
        val frustum = Frustum.fromWorldToClip(perspectiveMatrix)
        for (point in listOf(Vectors.of(0.0, 0.0, 1.5), Vectors.of(0.0, 0.0, 0.5), Vectors.of(3.0, 0.0, 1.5))) {
            assertEquals(frustum.contains(point), frustum.intersects(Box(point, point)), "$point")
        }
    }

    @Test
    fun perspectivePlanesMatchHomogeneousClipInequalities() {
        val frustum = Frustum.fromWorldToClip(perspectiveMatrix)
        assertPlane(0.0, 0.0, 1.0, 1.0, frustum.near)
        assertPlane(0.0, 0.0, -1.0, -2.0, frustum.far)
        assertEquals(0.0, frustum.top.evaluate(Vectors.of(0.0, 1.0, 1.0)))
        assertEquals(0.0, frustum.bottom.evaluate(Vectors.of(0.0, -1.0, 1.0)))
        assertTrue(frustum.top.normal.y < 0.0)
        assertTrue(frustum.bottom.normal.y > 0.0)
        assertMatchesClipVolume(perspectiveMatrix, frustum)
    }

    @Test
    fun worldToClipCompositionAccountsForCameraPositionAndOrientation() {
        val worldToView = Matrices.of(
            0.0, 0.0, 1.0, -30.0,
            0.0, 1.0, 0.0, -20.0,
            1.0, 0.0, 0.0, -10.0,
            0.0, 0.0, 0.0, 1.0,
        )
        val worldToClip = perspectiveMatrix * worldToView
        val frustum = Frustum.fromWorldToClip(worldToClip)

        assertTrue(frustum.contains(Vectors.of(11.5, 20.0, 30.0)))
        assertFalse(frustum.contains(Vectors.of(9.0, 20.0, 30.0)))
        assertFalse(frustum.contains(Vectors.of(11.5, 20.0, 33.0)))
        assertTrue(frustum.intersects(Box(11.25, 19.5, 29.5, 11.75, 20.5, 30.5)))
        assertFalse(frustum.intersects(Box(8.0, 19.5, 29.5, 9.0, 20.5, 30.5)))
        assertMatchesClipVolume(worldToClip, frustum, Vectors.of(10.0, 20.0, 30.0))
    }

    @Test
    fun extractionSamplesEachMatrixComponentOnceAndRetainsTheSnapshot() {
        val matrix = SampleOnceMatrix()
        val frustum = Frustum.fromWorldToClip(matrix)

        repeat(2) {
            assertTrue(frustum.contains(Vectors.of(0.0, 0.0, 0.5)))
            assertFalse(frustum.contains(Vectors.of(0.0, 0.0, -0.5)))
        }
        assertContentEquals(IntArray(16) { 1 }, matrix.reads)
    }

    @Test
    fun extractionRejectsDegenerateAndNonFiniteClippingPlanes() {
        for (matrix in listOf(Matrices.Zero, Matrices.Identity.withComponent(0, 0, Double.NaN), Matrices.Identity.withComponent(3, 3, Double.POSITIVE_INFINITY))) {
            assertFailsWith<IllegalArgumentException> { Frustum.fromWorldToClip(matrix) }
        }
    }

    @Test
    fun invalidBoxesAndDistanceOverflowRemainPotentialIntersections() {
        val frustum = Frustum.fromWorldToClip(Matrices.Identity)
        for (box in listOf(
            Box(Double.NaN, 0.0, .2, 3.0, .1, .8),
            Box(2.0, 0.0, .2, Double.POSITIVE_INFINITY, .1, .8),
            Box(3.0, 0.0, .2, 2.0, .1, .8),
        )) assertTrue(frustum.intersects(box))
        val mixed = Frustum.fromWorldToClip(
            Matrices.of(
                1.0, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0,
                1.0, 1.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0,
            )
        )
        val huge = Box(-1.7E308, -1.7E308, -1.7E308, -1.6E308, -1.6E308, -1.6E308)
        assertTrue(mixed.left.evaluate(huge.max).isFinite())
        assertTrue(mixed.left.evaluate(huge.max) < 0.0)
        assertEquals(Double.NEGATIVE_INFINITY, mixed.near.evaluate(huge.max))
        assertTrue(mixed.intersects(huge))
    }

    private val perspectiveMatrix = Matrices.of(
        1.0, 0.0, 0.0, 0.0,
        0.0, -1.0, 0.0, 0.0,
        0.0, 0.0, 2.0, -2.0,
        0.0, 0.0, 1.0, 0.0,
    )

    private fun assertMatchesClipVolume(matrix: MatrixView, frustum: Frustum, origin: VectorView = Vectors.Zero) {
        for (x in listOf(-3.0, -0.5, 0.0, 0.5, 3.0)) {
            for (y in listOf(-3.0, -0.5, 0.0, 0.5, 3.0)) {
                for (z in listOf(-1.0, 0.0, 0.5, 1.0, 1.5, 2.0, 3.0)) {
                    val point = origin + Vectors.of(x, y, z)
                    val clip = matrix.transformPosition(point)
                    val w = matrix.m30 * point.x + matrix.m31 * point.y + matrix.m32 * point.z + matrix.m33
                    val inside = clip.x >= -w && clip.x <= w &&
                            clip.y >= -w && clip.y <= w &&
                            clip.z >= 0.0 && clip.z <= w
                    assertEquals(inside, frustum.contains(point), "$point")
                }
            }
        }
    }

    private fun assertPlane(x: Double, y: Double, z: Double, offset: Double, actual: PlaneView) {
        assertEquals(x, actual.x, 1.0E-12, "x")
        assertEquals(y, actual.y, 1.0E-12, "y")
        assertEquals(z, actual.z, 1.0E-12, "z")
        assertEquals(offset, actual.w, 1.0E-12, "offset")
        assertTrue(actual.isNormalized())
    }

    private class SampleOnceMatrix : MatrixView {
        val reads = IntArray(16)
        override val m00: Double get() = read(0, 0)
        override val m01: Double get() = read(0, 1)
        override val m02: Double get() = read(0, 2)
        override val m03: Double get() = read(0, 3)
        override val m10: Double get() = read(1, 0)
        override val m11: Double get() = read(1, 1)
        override val m12: Double get() = read(1, 2)
        override val m13: Double get() = read(1, 3)
        override val m20: Double get() = read(2, 0)
        override val m21: Double get() = read(2, 1)
        override val m22: Double get() = read(2, 2)
        override val m23: Double get() = read(2, 3)
        override val m30: Double get() = read(3, 0)
        override val m31: Double get() = read(3, 1)
        override val m32: Double get() = read(3, 2)
        override val m33: Double get() = read(3, 3)

        private fun read(row: Int, column: Int): Double =
            if (++reads[row * 4 + column] == 1) Matrices.Identity[row, column] else Double.NaN
    }
}
