/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.BlendEquation
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Queries and changes fixed OpenGL state required by render passes and host-state
 * restoration.
 *
 * The newest mandatory core operations in this group are separate blend factors
 * and blend equations. Adapters may normalize the corresponding EXT operations.
 */
interface OpenGLStateFunctions {
    /** Whether the context exposes the rasterizer-discard enable state. */
    val supportsRasterizerDiscard: Boolean

    fun getError(): Int
    fun flush()
    fun getFloats(parameter: Int, destination: FloatBuffer)

    /**
     * Addresses one scissor without changing the other viewport-array entries. Contexts without
     * viewport arrays accept index zero and use their single scissor. A global glScissor call on
     * an array-capable context is not an equivalent implementation: it overwrites every box.
     */
    fun scissor(index: Int, x: Int, y: Int, width: Int, height: Int)
    fun getScissorBox(index: Int, destination: IntBuffer)
    fun isScissorEnabled(index: Int): Boolean
    fun setScissorEnabled(index: Int, enabled: Boolean)

    /**
     * Changes one draw buffer's mask when independent masks exist. Otherwise only index zero
     * is accepted and addresses the shared mask. Unlike the unindexed overload, this does not
     * overwrite unrelated independent masks on newer contexts.
     */
    fun colorMask(index: Int, red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)

    fun getInteger(parameter: Int): Int
    fun getIntegers(parameter: Int, destination: IntBuffer)
    fun isEnabled(capability: Int): Boolean

    fun enable(capability: Int)
    fun disable(capability: Int)
    fun viewport(x: Int, y: Int, width: Int, height: Int)

    fun blendFunctionSeparate(sourceRgbFactor: Int, destinationRgbFactor: Int, sourceAlphaFactor: Int, destinationAlphaFactor: Int)

    fun blendEquation(equation: BlendEquation)
    fun depthFunction(compareFunction: Int)
    fun depthMask(enabled: Boolean)
    fun cullFace(mode: Int)

    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)
    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float)
    fun clearDepth(depth: Double)
    fun clear(mask: Int)
}

context(function: OpenGLStateFunctions)
fun getInteger(parameter: Int): Int = function.getInteger(parameter)

context(function: OpenGLStateFunctions)
fun getIntegers(parameter: Int, destination: IntBuffer) = function.getIntegers(parameter, destination)

context(function: OpenGLStateFunctions)
fun isEnabled(capability: Int): Boolean = function.isEnabled(capability)

context(function: OpenGLStateFunctions)
fun enable(capability: Int) = function.enable(capability)

context(function: OpenGLStateFunctions)
fun disable(capability: Int) = function.disable(capability)

context(function: OpenGLStateFunctions)
fun viewport(x: Int, y: Int, width: Int, height: Int) = function.viewport(x, y, width, height)

context(function: OpenGLStateFunctions)
fun blendFunctionSeparate(
    sourceRgbFactor: Int,
    destinationRgbFactor: Int,
    sourceAlphaFactor: Int,
    destinationAlphaFactor: Int,
) = function.blendFunctionSeparate(sourceRgbFactor, destinationRgbFactor, sourceAlphaFactor, destinationAlphaFactor)

context(function: OpenGLStateFunctions)
fun blendEquation(equation: BlendEquation) = function.blendEquation(equation)

context(function: OpenGLStateFunctions)
fun depthFunction(compareFunction: Int) = function.depthFunction(compareFunction)

context(function: OpenGLStateFunctions)
fun depthMask(enabled: Boolean) = function.depthMask(enabled)

context(function: OpenGLStateFunctions)
fun cullFace(mode: Int) = function.cullFace(mode)

context(function: OpenGLStateFunctions)
fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
    function.colorMask(red, green, blue, alpha)

context(function: OpenGLStateFunctions)
fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
    function.clearColor(red, green, blue, alpha)

context(function: OpenGLStateFunctions)
fun clearDepth(depth: Double) = function.clearDepth(depth)

context(function: OpenGLStateFunctions)
fun clear(mask: Int) = function.clear(mask)

context(function: OpenGLStateFunctions)
val supportsRasterizerDiscard: Boolean
    get() = function.supportsRasterizerDiscard

context(function: OpenGLStateFunctions)
fun getError(): Int = function.getError()

context(function: OpenGLStateFunctions)
fun flush() = function.flush()

context(function: OpenGLStateFunctions)
fun getFloats(parameter: Int, destination: FloatBuffer) = function.getFloats(parameter, destination)

context(function: OpenGLStateFunctions)
fun scissor(index: Int, x: Int, y: Int, width: Int, height: Int) = function.scissor(index, x, y, width, height)

context(function: OpenGLStateFunctions)
fun getScissorBox(index: Int, destination: IntBuffer) = function.getScissorBox(index, destination)

context(function: OpenGLStateFunctions)
fun isScissorEnabled(index: Int): Boolean = function.isScissorEnabled(index)

context(function: OpenGLStateFunctions)
fun setScissorEnabled(index: Int, enabled: Boolean) = function.setScissorEnabled(index, enabled)

context(function: OpenGLStateFunctions)
fun colorMask(index: Int, red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) = function.colorMask(index, red, green, blue, alpha)
