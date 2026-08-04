/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Matrix4Test {
    @Test
    fun columnMajorMatrixOwnsItsValues() {
        val values = FloatArray(16) { it.toFloat() }
        val matrix = Matrix4.columnMajor(values)

        values[6] = -1.0F

        assertEquals(expected = 6.0F, actual = matrix[2, 1])
    }

    @Test
    fun columnMajorMatrixRequiresSixteenValues() {
        assertFailsWith<IllegalArgumentException> { Matrix4.columnMajor(FloatArray(15)) }
    }
}
