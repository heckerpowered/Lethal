/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MatrixSnapshotTest {
    private val sample = Matrices.of(
        2.0, 1.0, 0.0, 4.0,
        0.0, 3.0, 0.0, -3.0,
        0.0, 0.0, -4.0, 2.0,
        0.0, 0.0, 0.0, 1.0
    )

    @Test
    fun copyReadsEachSourceComponentOnceAndOwnsTheSample() {
        val view = SingleSampleMatrix(sample)
        val copy = Matrices.copyOf(view)
        assertMatrix(sample, copy)
        assertMatrix(sample, copy)
        view.assertReadOnce()
    }

    @Test
    fun bothDeterminantsUseOneSamplePerComponent() {
        val full = SingleSampleMatrix(sample)
        val linear = SingleSampleMatrix(sample)
        assertEquals(-24.0, full.determinant)
        assertEquals(-24.0, linear.linearDeterminant)
        full.assertReadOnce()
        linear.assertReadOnce()
    }

    @Test
    fun inversionUsesTheSameSampleThroughoutElimination() {
        val view = SingleSampleMatrix(sample)
        assertMatrix(sample.inverse(), assertNotNull(view.inverseOrNull()))
        view.assertReadOnce()
    }

    @Test
    fun affineValidationAndConstructionShareTheSample() {
        val view = SingleSampleMatrix(sample)
        assertMatrix(sample, view.toAffine().toMatrix4())
        view.assertReadOnce()
    }

    @Test
    fun inverseAffineOperationsValidateAndInvertTheSameSample() {
        val positionView = SingleSampleMatrix(sample)
        val vectorView = SingleSampleMatrix(sample)
        assertVector(Vectors.One, positionView.inverseTransformPosition(sample.transformPosition(Vectors.One)))
        assertVector(Vectors.One, vectorView.inverseTransformVector(sample.transformVector(Vectors.One)))
        positionView.assertReadOnce()
        vectorView.assertReadOnce()
    }

    @Test
    fun generatorEvaluatesEachCoordinateOnceInRowMajorOrder() {
        val coordinates = mutableListOf<Pair<Int, Int>>()
        val matrix = Matrices.generate { row, column ->
            coordinates.add(row to column)
            (row * 4 + column).toDouble()
        }
        assertEquals((0..3).flatMap { row -> (0..3).map { column -> row to column } }, coordinates)
        assertContentEquals(DoubleArray(16) { it.toDouble() }, matrix.toRowMajorArray())
    }

    private class SingleSampleMatrix(private val sample: MatrixView) : MatrixView {
        private val reads = IntArray(16)

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

        fun assertReadOnce() {
            assertContentEquals(IntArray(16) { 1 }, reads)
        }

        private fun read(row: Int, column: Int): Double {
            val index = row * 4 + column
            return if (++reads[index] == 1) sample[row, column] else Double.NaN
        }
    }
}
