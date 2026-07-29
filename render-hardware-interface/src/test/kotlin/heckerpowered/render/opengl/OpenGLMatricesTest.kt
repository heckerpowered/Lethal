/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.Matrix4
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenGLMatricesTest {
    @Test
    fun currentModelViewProjectionMultipliesCompleteColumnMajorMatrices() {
        val memoryStack = MemoryStack()
        val actual = OpenGLMatrices.currentModelViewProjection(MatrixBinding(memoryStack), memoryStack)

        assertEquals(expected = ExpectedProduct, actual = actual)
    }

    private class MatrixBinding(private val memoryStack: MemoryStack) : OpenGLBinding {
        override fun getFloat(parameter: Int, outputAddress: NativeAddress) {
            val matrix = when (parameter) {
                MODELVIEW_MATRIX_PARAMETER -> ModelView
                PROJECTION_MATRIX_PARAMETER -> Projection
                else -> error("Unexpected matrix parameter $parameter")
            }
            memoryStack.storeFloats(outputAddress, matrix)
        }

        override fun queryContextCapabilities(): OpenGLContextCapabilities = unsupported()
        override fun getInteger(parameter: Int, outputAddress: NativeAddress) = unsupported()
        override fun getIndexedInteger(parameter: Int, index: Int): Int = unsupported()
        override fun uniformMatrix4(location: Int, transpose: Boolean, valuesAddress: NativeAddress) = unsupported()
        override fun bufferSubData(target: Int, offsetBytes: Long, sizeBytes: Long, dataAddress: NativeAddress) = unsupported()
        override fun bindBufferBase(target: Int, index: Int, buffer: Int) = unsupported()
        override fun getUniformBlockIndex(program: Int, name: String): Int = unsupported()
        override fun uniformBlockBinding(program: Int, blockIndex: Int, binding: Int) = unsupported()

        private fun unsupported(): Nothing = error("The matrix test does not use this OpenGL operation")
    }

    private companion object {
        const val MODELVIEW_MATRIX_PARAMETER = 0x0BA6
        const val PROJECTION_MATRIX_PARAMETER = 0x0BA7

        val ModelView = translationMatrixValues(5.0F, 6.0F, 7.0F)
        val Projection = floatArrayOf(
            2.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 3.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 4.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F,
        )
        val ExpectedProduct = Matrix4.columnMajor(
            floatArrayOf(
                2.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 3.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 4.0F, 0.0F,
                10.0F, 18.0F, 28.0F, 1.0F,
            ),
        )

        @Suppress("SameParameterValue")
        private fun translationMatrixValues(translationX: Float, translationY: Float, translationZ: Float): FloatArray {
            return floatArrayOf(
                1.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                translationX, translationY, translationZ, 1.0F,
            )
        }
    }
}
