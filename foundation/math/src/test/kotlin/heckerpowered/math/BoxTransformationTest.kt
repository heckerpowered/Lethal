/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class BoxTransformationTest {
    @Test
    fun identityAndTranslationPreserveBoxSize() {
        val box = Box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)

        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, box.transformedBy(AffineTransforms.Identity))
        assertBox(6.0, 6.0, 6.0, 11.0, 13.0, 15.0, box.transformedBy(AffineTransforms.fromTranslation(Vectors.of(7.0, 8.0, 9.0))))
    }

    @Test
    fun rotationReordersBoundsAndKeepsThemAxisAligned() {
        val box = Box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)
        val quarterTurn = AffineTransforms.of(
            axisX = Vectors.UnitY,
            axisY = Vectors.NegativeUnitX,
            axisZ = Vectors.UnitZ,
        )

        assertBox(-5.0, -1.0, -3.0, 2.0, 4.0, 6.0, box.transformedBy(quarterTurn))
    }

    @Test
    fun reflectionNonuniformScaleAndShearProduceTightBounds() {
        val box = Box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)
        val transform = AffineTransforms.of(
            axisX = Vectors.of(-2.0, 0.0, 0.0),
            axisY = Vectors.of(1.0, 3.0, 0.0),
            axisZ = Vectors.of(0.0, -1.0, 4.0),
            translation = Vectors.of(5.0, 7.0, 9.0),
        )
        val transformed = box.transformedBy(transform)

        assertBox(-5.0, -5.0, -3.0, 12.0, 25.0, 33.0, transformed)

        val corners = box.vertices().map { transform.transformPosition(it) }
        assertVector(corners.minOf { it.x }, corners.minOf { it.y }, corners.minOf { it.z }, transformed.min)
        assertVector(corners.maxOf { it.x }, corners.maxOf { it.y }, corners.maxOf { it.z }, transformed.max)
        for (corner in corners) {
            assertTrue(transformed.containsOrOn(corner))
        }
    }

    @Test
    fun singularTransformsCanFlattenOrCollapseTheBox() {
        val box = Box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)
        val flattened = AffineTransforms.of(axisX = Vectors.Zero, translation = Vectors.of(5.0, 7.0, 9.0))
        val collapsed = AffineTransforms.of(
            axisX = Vectors.Zero,
            axisY = Vectors.Zero,
            axisZ = Vectors.Zero,
            translation = Vectors.of(5.0, 7.0, 9.0),
        )

        assertBox(5.0, 5.0, 6.0, 5.0, 12.0, 15.0, box.transformedBy(flattened))
        assertBox(5.0, 7.0, 9.0, 5.0, 7.0, 9.0, box.transformedBy(collapsed))
    }

    @Test
    fun pointBoxesRemainPointsUnderGeneralAffineTransforms() {
        val box = Box(1.0, 2.0, 3.0, 1.0, 2.0, 3.0)
        val transform = AffineTransforms.of(
            axisX = Vectors.of(-2.0, 0.0, 0.0),
            axisY = Vectors.of(1.0, 3.0, 0.0),
            axisZ = Vectors.of(0.0, -1.0, 4.0),
            translation = Vectors.of(5.0, 7.0, 9.0),
        )

        assertBox(5.0, 10.0, 21.0, 5.0, 10.0, 21.0, box.transformedBy(transform))
    }

    @Test
    fun transformationSamplesEveryBoxBoundAndTransformComponentOnce() {
        val boxReads = IntArray(6)
        val box = object : BoxView {
            override val minX: Double get() = if (++boxReads[0] == 1) -1.0 else Double.NaN
            override val minY: Double get() = if (++boxReads[1] == 1) -2.0 else Double.NaN
            override val minZ: Double get() = if (++boxReads[2] == 1) -3.0 else Double.NaN
            override val maxX: Double get() = if (++boxReads[3] == 1) 4.0 else Double.NaN
            override val maxY: Double get() = if (++boxReads[4] == 1) 5.0 else Double.NaN
            override val maxZ: Double get() = if (++boxReads[5] == 1) 6.0 else Double.NaN
        }
        val axisX = SampleOnceVector(-2.0, 0.0, 0.0)
        val axisY = SampleOnceVector(1.0, 3.0, 0.0)
        val axisZ = SampleOnceVector(0.0, -1.0, 4.0)
        val translation = SampleOnceVector(5.0, 7.0, 9.0)
        val transform = AffineTransforms.of(axisX, axisY, axisZ, translation)

        assertBox(-5.0, -5.0, -3.0, 12.0, 25.0, 33.0, box.transformedBy(transform))
        assertContentEquals(IntArray(6) { 1 }, boxReads)
        for (vector in listOf(axisX, axisY, axisZ, translation)) {
            assertContentEquals(intArrayOf(1, 1, 1), vector.reads)
        }
    }

    private class SampleOnceVector(private val firstX: Double, private val firstY: Double, private val firstZ: Double) : VectorView {
        val reads = IntArray(3)
        override val x: Double get() = if (++reads[0] == 1) firstX else Double.NaN
        override val y: Double get() = if (++reads[1] == 1) firstY else Double.NaN
        override val z: Double get() = if (++reads[2] == 1) firstZ else Double.NaN
    }
}
