/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.shader.ShaderStage

/**
 * The name of an OpenGL shader object.
 *
 * A name identifies an object in the current OpenGL context but does not own
 * that object or manage its lifetime.
 */
@JvmInline
value class ShaderName(val value: Int) {
    companion object {
        val None = ShaderName(0)
    }
}

/** The name of an OpenGL program object. */
@JvmInline
value class ProgramName(val value: Int) {
    companion object {
        val None = ProgramName(0)
    }
}

/** The name of an OpenGL buffer object. */
@JvmInline
value class BufferName(val value: Int) {
    companion object {
        val None = BufferName(0)
    }
}

/** The name of an OpenGL texture object. */
@JvmInline
value class TextureName(val value: Int) {
    companion object {
        val None = TextureName(0)
    }
}

/** The name of an OpenGL framebuffer object. */
@JvmInline
value class FramebufferName(val value: Int) {
    companion object {
        val Default = FramebufferName(0)
    }
}

/** The name of an OpenGL renderbuffer object. */
@JvmInline
value class RenderbufferName(val value: Int) {
    companion object {
        val None = RenderbufferName(0)
    }
}

/** The name of an OpenGL vertex array object. */
@JvmInline
value class VertexArrayName(val value: Int) {
    companion object {
        val None = VertexArrayName(0)
    }
}

/** The name of an OpenGL sampler object. */
@JvmInline
value class SamplerName(val value: Int) {
    companion object {
        val None = SamplerName(0)
    }
}

/**
 * The location of a uniform in one linked OpenGL program.
 *
 * Locations belong to the program from which they were queried and become
 * invalid when that program is relinked or deleted.
 */
@JvmInline
value class UniformLocation(val value: Int) {
    init {
        require(value >= -1) { "Uniform locations must be -1 or non-negative" }
    }

    val isPresent: Boolean
        get() = value >= 0

    companion object {
        val Missing = UniformLocation(-1)
    }
}

/** The index assigned to a generic vertex attribute in a linked program. */
@JvmInline
value class VertexAttributeIndex(val value: Int) {
    init {
        require(value >= 0) { "Vertex attribute indices must be non-negative" }
    }
}

/**
 * The index of a named uniform block in one linked OpenGL program.
 *
 * `GL_INVALID_INDEX` has the unsigned bit pattern `0xFFFFFFFF` and is represented
 * as `-1` by the JVM `Int` used by the adapters.
 */
@JvmInline
value class UniformBlockIndex(val value: Int) {
    init {
        require(value >= -1) { "Uniform block indices must be -1 or non-negative" }
    }

    val isPresent: Boolean
        get() = value >= 0

    companion object {
        val Missing = UniformBlockIndex(-1)
    }
}

/** A context-wide uniform-buffer binding point. */
@JvmInline
value class UniformBufferBindingIndex(val value: Int) {
    init {
        require(value >= 0) { "Uniform buffer binding indices must be non-negative" }
    }
}

/**
 * A zero-based texture image unit.
 *
 * Adapters translate this index to the binding-specific `GL_TEXTURE0 + index`
 * token when selecting the active texture unit.
 */
@JvmInline
value class TextureUnit(val value: Int) {
    init {
        require(value >= 0) { "Texture units must be non-negative" }
    }
}

/** Shader stages compiled by the current OpenGL backend. */
enum class ShaderType {
    Vertex,
    Fragment,
}

/** Buffer binding targets used by the current OpenGL backend. */
enum class BufferTarget {
    Array,
    ElementArray,
}

/** Buffer allocation hints used by the current OpenGL backend. */
enum class BufferUsage {
    StaticDraw,
    DynamicDraw,
}

/** Primitive assembly modes exposed by the current RHI draw path. */
enum class PrimitiveMode {
    Lines,
    Triangles,
    TriangleStrip,
}

/** Basic equations accepted by OpenGL blend-equation state. */
enum class BlendEquation {
    Add,
    Subtract,
    ReverseSubtract,
    Min,
    Max,
}

/** Shader-output color-clamping modes used by the backend. */
enum class ColorClampMode {
    Always,
    FixedOnly,
    Never,
}

fun ShaderStage.toOpenGLShaderType(): ShaderType = when (this) {
    ShaderStage.Vertex -> ShaderType.Vertex
    ShaderStage.Fragment -> ShaderType.Fragment
}
