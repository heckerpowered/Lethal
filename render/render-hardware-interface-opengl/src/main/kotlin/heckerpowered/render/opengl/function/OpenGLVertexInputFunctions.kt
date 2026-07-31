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