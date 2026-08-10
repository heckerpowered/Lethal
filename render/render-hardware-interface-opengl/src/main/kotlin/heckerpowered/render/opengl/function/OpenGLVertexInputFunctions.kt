/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.VertexAttributeIndex

/**
 * Configures floating-point vertex attributes consumed by shader programs.
 *
 * Adapters may normalize the corresponding ARB generic vertex-attribute entry
 * points when the OpenGL 2.0 core functions are absent.
 */
interface OpenGLVertexInputFunctions {
    fun enableVertexAttribute(index: VertexAttributeIndex)
    fun disableVertexAttribute(index: VertexAttributeIndex)

    fun vertexAttributePointer(index: VertexAttributeIndex, componentCount: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Long)
}

context(function: OpenGLVertexInputFunctions)
fun enableVertexAttribute(index: VertexAttributeIndex) = function.enableVertexAttribute(index)

context(function: OpenGLVertexInputFunctions)
fun disableVertexAttribute(index: VertexAttributeIndex) = function.disableVertexAttribute(index)

context(function: OpenGLVertexInputFunctions)
fun vertexAttributePointer(
    index: VertexAttributeIndex,
    componentCount: Int,
    type: Int,
    normalized: Boolean,
    strideBytes: Int,
    offsetBytes: Long,
) = function.vertexAttributePointer(index, componentCount, type, normalized, strideBytes, offsetBytes)
