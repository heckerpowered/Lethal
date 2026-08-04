/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.MemoryFrame
import heckerpowered.render.memory.NativeAddress

/**
 * Immutable 4 x 4 matrix stored in column-major order for graphics APIs.
 */
class Matrix4 private constructor(private val columnMajorValues: FloatArray) {
    operator fun get(row: Int, column: Int): Float {
        require(row in 0..3 && column in 0..3) { "Matrix coordinates must be between zero and three" }
        return columnMajorValues[column * 4 + row]
    }

    fun writeColumnMajor(memoryFrame: MemoryFrame, destinationAddress: NativeAddress) {
        memoryFrame.storeFloats(destinationAddress, columnMajorValues)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is Matrix4 && columnMajorValues.contentEquals(other.columnMajorValues)
    }

    override fun hashCode(): Int = columnMajorValues.contentHashCode()

    companion object {
        val Identity = ownColumnMajor(
            floatArrayOf(
                1.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F,
            ),
        )

        fun columnMajor(values: FloatArray): Matrix4 {
            require(values.size == ELEMENT_COUNT) { "A 4 x 4 matrix requires 16 values" }
            return ownColumnMajor(values.copyOf())
        }

        internal fun ownColumnMajor(values: FloatArray): Matrix4 {
            require(values.size == ELEMENT_COUNT) { "A 4 x 4 matrix requires 16 values" }
            return Matrix4(values)
        }

        private const val ELEMENT_COUNT = 16
    }
}
