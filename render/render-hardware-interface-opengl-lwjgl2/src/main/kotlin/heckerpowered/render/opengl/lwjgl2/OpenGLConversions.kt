/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.opengl.*
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

internal fun ShaderType.toOpenGL(): Int = when (this) {
    ShaderType.Vertex -> GL20.GL_VERTEX_SHADER
    ShaderType.Fragment -> GL20.GL_FRAGMENT_SHADER
}

internal fun BufferTarget.toOpenGL(): Int = when (this) {
    BufferTarget.Array -> GL15.GL_ARRAY_BUFFER
    BufferTarget.ElementArray -> GL15.GL_ELEMENT_ARRAY_BUFFER
}

internal fun BufferTarget.toOpenGLBinding(): Int = when (this) {
    BufferTarget.Array -> GL15.GL_ARRAY_BUFFER_BINDING
    BufferTarget.ElementArray -> GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING
}

internal fun BufferUsage.toOpenGL(): Int = when (this) {
    BufferUsage.StaticDraw -> GL15.GL_STATIC_DRAW
    BufferUsage.DynamicDraw -> GL15.GL_DYNAMIC_DRAW
}

internal fun PrimitiveMode.toOpenGL(): Int = when (this) {
    PrimitiveMode.Lines -> GL11.GL_LINES
    PrimitiveMode.Triangles -> GL11.GL_TRIANGLES
    PrimitiveMode.TriangleStrip -> GL11.GL_TRIANGLE_STRIP
}

internal fun ColorClampMode.toOpenGL(): Int = when (this) {
    ColorClampMode.Unclamped -> GL11.GL_FALSE
    ColorClampMode.FixedOnly -> GL30.GL_FIXED_ONLY
}
