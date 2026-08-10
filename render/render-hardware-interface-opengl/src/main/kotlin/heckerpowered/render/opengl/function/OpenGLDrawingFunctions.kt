/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.PrimitiveMode

/**
 * Submits non-indexed primitives used by the current RHI draw command.
 */
interface OpenGLDrawingFunctions {
    fun drawArrays(mode: PrimitiveMode, firstVertex: Int, vertexCount: Int)
}

context(function: OpenGLDrawingFunctions)
fun drawArrays(mode: PrimitiveMode, firstVertex: Int, vertexCount: Int) =
    function.drawArrays(mode, firstVertex, vertexCount)
