/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.VertexArrayName

/**
 * Owns vertex input state in contexts that expose vertex array objects.
 *
 * Adapters may normalize `ARB_vertex_array_object` entry points. When this
 * capability is absent, the backend restores the required vertex input state
 * directly before drawing.
 */
interface OpenGLVertexArrayFunctions {
    fun createVertexArray(): VertexArrayName
    fun getBoundVertexArray(): VertexArrayName
    fun bindVertexArray(vertexArray: VertexArrayName)
    fun deleteVertexArray(vertexArray: VertexArrayName)
}

context(function: OpenGLVertexArrayFunctions)
fun createVertexArray(): VertexArrayName = function.createVertexArray()

context(function: OpenGLVertexArrayFunctions)
fun getBoundVertexArray(): VertexArrayName = function.getBoundVertexArray()

context(function: OpenGLVertexArrayFunctions)
fun bindVertexArray(vertexArray: VertexArrayName) = function.bindVertexArray(vertexArray)

context(function: OpenGLVertexArrayFunctions)
fun deleteVertexArray(vertexArray: VertexArrayName) = function.deleteVertexArray(vertexArray)
