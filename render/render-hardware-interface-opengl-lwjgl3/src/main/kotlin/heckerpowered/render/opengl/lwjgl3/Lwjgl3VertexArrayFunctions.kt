/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.VertexArrayName
import heckerpowered.render.opengl.function.OpenGLVertexArrayFunctions
import org.lwjgl.opengl.ARBVertexArrayObject
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLCapabilities

internal class Lwjgl3VertexArrayFunctions private constructor(
    private val entryPoints: Lwjgl3VertexArrayEntryPoints,
) : OpenGLVertexArrayFunctions {
    override fun createVertexArray(): VertexArrayName {
        val name = when (entryPoints) {
            Lwjgl3VertexArrayEntryPoints.Core -> GL30.glGenVertexArrays()
            Lwjgl3VertexArrayEntryPoints.ARB -> ARBVertexArrayObject.glGenVertexArrays()
        }
        return VertexArrayName(name)
    }

    override fun getBoundVertexArray(): VertexArrayName {
        return VertexArrayName(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING))
    }

    override fun bindVertexArray(vertexArray: VertexArrayName) = when (entryPoints) {
        Lwjgl3VertexArrayEntryPoints.Core -> GL30.glBindVertexArray(vertexArray.value)
        Lwjgl3VertexArrayEntryPoints.ARB -> ARBVertexArrayObject.glBindVertexArray(vertexArray.value)
    }

    override fun deleteVertexArray(vertexArray: VertexArrayName) = when (entryPoints) {
        Lwjgl3VertexArrayEntryPoints.Core -> GL30.glDeleteVertexArrays(vertexArray.value)
        Lwjgl3VertexArrayEntryPoints.ARB -> ARBVertexArrayObject.glDeleteVertexArrays(vertexArray.value)
    }

    companion object {
        fun create(capabilities: GLCapabilities): OpenGLVertexArrayFunctions? {
            val entryPoints = when {
                capabilities.OpenGL30 -> Lwjgl3VertexArrayEntryPoints.Core
                capabilities.GL_ARB_vertex_array_object -> Lwjgl3VertexArrayEntryPoints.ARB
                else -> return null
            }
            return Lwjgl3VertexArrayFunctions(entryPoints)
        }
    }
}
