/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.parameter

import heckerpowered.math.MatrixView
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues

/**
 * Supplies shader parameters that can be computed from existing values and an object's transform.
 *
 * Normal transformation is computed only when requested, allowing unlit drawing to use a singular
 * model transform without requiring an inverse. The optional `normalFromLocal` value is the
 * inverse transpose of the transform's linear part, encoded as a column-major Float mat4 after
 * the Double calculation. A non-finite or nearly singular determinant is rejected.
 *
 * Numeric packing runs in declaration order, so a later block may consume an earlier result.
 * Derived names must be absent from the supplied values; they cannot override a provider.
 */
class ParameterDerivations(
    private val requireNormalMatrix: Boolean = false,
    packing: List<NumericPacking> = emptyList(),
) {
    private val packing = packing.toList()

    fun resolve(input: ParameterValues, localToWorld: MatrixView): ParameterValues {
        val initialValues = if (requireNormalMatrix) {
            require(ParameterName("normalFromLocal") !in input.values)
            input.replacing("normalFromLocal", normalMatrix(localToWorld))
        } else input
        return packing.fold(initialValues) { values, block -> block.derive(values) }
    }
}

internal fun normalMatrix(localToWorld: MatrixView): NumericParameterValue {
    val linearColumns = Array(3) { column -> DoubleArray(3) { row -> localToWorld[row, column] } }
    fun cross(firstColumn: DoubleArray, secondColumn: DoubleArray) = doubleArrayOf(
        firstColumn[1] * secondColumn[2] - firstColumn[2] * secondColumn[1],
        firstColumn[2] * secondColumn[0] - firstColumn[0] * secondColumn[2],
        firstColumn[0] * secondColumn[1] - firstColumn[1] * secondColumn[0],
    )

    val cofactorColumns = arrayOf(
        cross(linearColumns[1], linearColumns[2]),
        cross(linearColumns[2], linearColumns[0]),
        cross(linearColumns[0], linearColumns[1]),
    )
    val determinant = (0..2).sumOf { row -> linearColumns[0][row] * cofactorColumns[0][row] }
    require(determinant.isFinite() && kotlin.math.abs(determinant) > 1e-20) { "Lighting requires an invertible model transform" }

    // A mat4-sized block avoids exposing a packed 3x3 as if it satisfied std140's column alignment.
    val normalFromLocal = FloatArray(16)
    for (column in 0..2) {
        for (row in 0..2) {
            normalFromLocal[column * 4 + row] = (cofactorColumns[column][row] / determinant).toFloat()
        }
    }
    normalFromLocal[15] = 1f
    return NumericParameterValue.floats(*normalFromLocal)
}
