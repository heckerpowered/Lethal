/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import java.nio.IntBuffer

/**
 * Queries and changes fixed OpenGL state required by render passes and host-state
 * restoration.
 *
 * The newest mandatory core operations in this group are separate blend factors
 * and blend equations. Adapters may normalize the corresponding EXT operations.
 */
interface OpenGLStateFunctions {
    fun getInteger(parameter: Int): Int
    fun getIntegers(parameter: Int, destination: IntBuffer)
    fun isEnabled(capability: Int): Boolean

    fun enable(capability: Int)
    fun disable(capability: Int)
    fun viewport(x: Int, y: Int, width: Int, height: Int)

    fun blendFunctionSeparate(sourceRgbFactor: Int, destinationRgbFactor: Int, sourceAlphaFactor: Int, destinationAlphaFactor: Int)

    fun blendEquation(equation: Int)
    fun depthFunction(compareFunction: Int)
    fun depthMask(enabled: Boolean)
    fun cullFace(mode: Int)

    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)
    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float)
    fun clearDepth(depth: Double)
    fun clear(mask: Int)
}