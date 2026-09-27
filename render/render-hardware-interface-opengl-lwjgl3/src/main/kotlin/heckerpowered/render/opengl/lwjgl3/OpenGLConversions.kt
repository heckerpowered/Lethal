/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.*
import org.lwjgl.opengl.*

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
    ColorClampMode.Always -> GL11.GL_TRUE
    ColorClampMode.Never -> GL11.GL_FALSE
    ColorClampMode.FixedOnly -> GL30.GL_FIXED_ONLY
}

internal fun BlendEquation.toOpenGL(): Int = when (this) {
    BlendEquation.Add -> GL14.GL_FUNC_ADD
    BlendEquation.Subtract -> GL14.GL_FUNC_SUBTRACT
    BlendEquation.ReverseSubtract -> GL14.GL_FUNC_REVERSE_SUBTRACT
    BlendEquation.Min -> GL14.GL_MIN
    BlendEquation.Max -> GL14.GL_MAX
}
