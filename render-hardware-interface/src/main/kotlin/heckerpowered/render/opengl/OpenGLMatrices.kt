/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.Matrix4
import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.MemoryFrame
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.NativeStruct
import org.lwjgl.opengl.GL11

/**
 * Reads matrices from the active compatibility-profile OpenGL context.
 */
internal object OpenGLMatrices {
    fun currentModelViewProjection(binding: OpenGLBinding, memoryStack: MemoryStack): Matrix4 {
        return memoryStack.allocOpenGLMatrixState { modelView, projection ->
            binding.getFloat(GL11.GL_MODELVIEW_MATRIX, modelView)
            binding.getFloat(GL11.GL_PROJECTION_MATRIX, projection)
            val output = FloatArray(16)
            multiply(projection, modelView, output)
            Matrix4.ownColumnMajor(output)
        }
    }

    private fun MemoryFrame.multiply(left: NativeAddress, right: NativeAddress, output: FloatArray) {
        loadFloats(right, output)
        for (column in 0 until 4) {
            val columnOffset = column * 4
            val right0 = output[columnOffset]
            val right1 = output[columnOffset + 1]
            val right2 = output[columnOffset + 2]
            val right3 = output[columnOffset + 3]

            for (row in 0 until 4) {
                val rowOffset = row * Float.SIZE_BYTES
                val left0 = loadFloat(left + rowOffset)
                val left1 = loadFloat(left + (4 * Float.SIZE_BYTES + rowOffset))
                val left2 = loadFloat(left + (8 * Float.SIZE_BYTES + rowOffset))
                val left3 = loadFloat(left + (12 * Float.SIZE_BYTES + rowOffset))

                output[columnOffset + row] = left0 * right0 + left1 * right1 + left2 * right2 + left3 * right3
            }
        }
    }
}

@NativeStruct
internal interface OpenGLMatrixState {
    @FloatElements(16)
    val modelView: NativeAddress

    @FloatElements(16)
    val projection: NativeAddress
}
