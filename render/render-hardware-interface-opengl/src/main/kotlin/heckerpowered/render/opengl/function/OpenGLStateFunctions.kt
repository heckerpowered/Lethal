/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.BlendEquation
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
