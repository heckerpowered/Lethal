/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.parameter

import heckerpowered.math.Matrices
import heckerpowered.math.MatrixView
import heckerpowered.math.inverseOrNull
import heckerpowered.render.engine.material.parameter.NumericParameterValue
import heckerpowered.render.engine.material.parameter.ParameterName
import heckerpowered.render.engine.material.parameter.ParameterValues

/**
 * Supplies shader parameters that can be computed from existing values and an object's transform.
 *
 * Normal transformation is computed only when requested, allowing unlit drawing to use a singular
 * model transform without requiring an inverse. The optional `normalFromLocal` value is the
 * inverse transpose of the transform's linear part, encoded as a column-major Float mat4 after
 * the Double calculation. The linear part must have a finite Double inverse; only zero pivots
 * are rejected, without imposing an absolute scale threshold or a condition-number test.
 * Float encoding rejects overflow and nonzero values that underflow to zero.
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
    val linear = Matrices.generate { row, column ->
        if (row < 3 && column < 3) localToWorld[row, column]
        else if (row == column) 1.0 else 0.0
    }
    val inverse = requireNotNull(linear.inverseOrNull()) { "Lighting requires a finite inverse of the model transform's linear part" }

    // A mat4-sized block avoids exposing a packed 3x3 as if it satisfied std140's column alignment.
    val normalFromLocal = FloatArray(16)
    for (column in 0..2) {
        for (row in 0..2) {
            val component = inverse[column, row]
            val encoded = component.toFloat()
            require(encoded.isFinite() && (component == 0.0 || encoded != 0f)) { "Normal transform component cannot be represented as a finite nonzero Float" }
            normalFromLocal[column * 4 + row] = encoded
        }
    }
    normalFromLocal[15] = 1f
    return NumericParameterValue.floats(*normalFromLocal)
}
