/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.opengl.function.OpenGLFunctionsProvider
import org.lwjgl.opengl.*

/** Loads the thin LWJGL 2 adapter for the context current on the calling thread. */
class Lwjgl2OpenGLFunctionsProvider : OpenGLFunctionsProvider {
    override fun create(): OpenGLFunctions {
        val capabilities = currentCapabilities()
        val missingCapabilities = mutableListOf<String>()

        if (!capabilities.OpenGL11) {
            missingCapabilities += "OpenGL 1.1 state, texture, and drawing operations"
        }

        val shaderEntryPoints = when {
            capabilities.OpenGL20 -> Lwjgl2ShaderEntryPoints.Core
            capabilities.GL_ARB_shader_objects && capabilities.GL_ARB_vertex_shader && capabilities.GL_ARB_fragment_shader -> Lwjgl2ShaderEntryPoints.ARB
            else -> null
        }
        if (shaderEntryPoints == null) {
            missingCapabilities += "shader objects (OpenGL 2.0 or ARB shader, vertex-shader, and fragment-shader extensions)"
        }

        val bufferEntryPoints = when {
            capabilities.OpenGL15 -> Lwjgl2BufferEntryPoints.Core
            capabilities.GL_ARB_vertex_buffer_object -> Lwjgl2BufferEntryPoints.ARB
            else -> null
        }
        if (bufferEntryPoints == null) {
            missingCapabilities += "buffer objects (OpenGL 1.5 or ARB_vertex_buffer_object)"
        }

        val multitextureEntryPoints = when {
            capabilities.OpenGL13 -> Lwjgl2MultitextureEntryPoints.Core
            capabilities.GL_ARB_multitexture -> Lwjgl2MultitextureEntryPoints.ARB
            else -> null
        }
        if (multitextureEntryPoints == null) {
            missingCapabilities += "active texture selection (OpenGL 1.3 or ARB_multitexture)"
        }

        val vertexInputEntryPoints = when {
            capabilities.OpenGL20 -> Lwjgl2VertexInputEntryPoints.Core
            capabilities.GL_ARB_vertex_shader -> Lwjgl2VertexInputEntryPoints.ARB
            else -> null
        }
        if (vertexInputEntryPoints == null) {
            missingCapabilities += "generic vertex attributes (OpenGL 2.0 or ARB_vertex_shader)"
        }

        val blendFunctionEntryPoints = when {
            capabilities.OpenGL14 -> Lwjgl2BlendFunctionEntryPoints.Core
            capabilities.GL_EXT_blend_func_separate -> Lwjgl2BlendFunctionEntryPoints.EXT
            else -> null
        }
        if (blendFunctionEntryPoints == null) {
            missingCapabilities += "separate blend factors (OpenGL 1.4 or EXT_blend_func_separate)"
        }

        val blendEquationEntryPoints = when {
            capabilities.OpenGL14 -> Lwjgl2BlendEquationEntryPoints.Core
            capabilities.GL_EXT_blend_minmax && capabilities.GL_EXT_blend_subtract -> Lwjgl2BlendEquationEntryPoints.EXT
            else -> null
        }
        if (blendEquationEntryPoints == null) {
            missingCapabilities += "blend equations (OpenGL 1.4 or EXT_blend_minmax + EXT_blend_subtract)"
        }

        if (missingCapabilities.isNotEmpty()) {
            throw IllegalStateException(
                "LWJGL 2 cannot create the mandatory OpenGL function baseline; missing ${missingCapabilities.joinToString()}",
            )
        }

        val selectedBufferEntryPoints = requireNotNull(bufferEntryPoints)
        return Lwjgl2OpenGLFunctions(
            shaderEntryPoints = requireNotNull(shaderEntryPoints),
            bufferEntryPoints = selectedBufferEntryPoints,
            multitextureEntryPoints = requireNotNull(multitextureEntryPoints),
            vertexInputEntryPoints = requireNotNull(vertexInputEntryPoints),
            blendFunctionEntryPoints = requireNotNull(blendFunctionEntryPoints),
            blendEquationEntryPoints = requireNotNull(blendEquationEntryPoints),
            framebuffers = Lwjgl2FramebufferFunctions.create(capabilities),
            vertexArrays = Lwjgl2VertexArrayFunctions.create(capabilities),
            uniformBuffers = Lwjgl2UniformBufferFunctions.create(capabilities, selectedBufferEntryPoints),
            samplers = Lwjgl2SamplerFunctions.create(capabilities),
            shaderColorClamping = Lwjgl2ShaderColorClampingFunctions.create(capabilities),
        )
    }

    private fun currentCapabilities(): ContextCapabilities {
        return try {
            GLContext.getCapabilities()
        } catch (cause: RuntimeException) {
            throw IllegalStateException(
                "LWJGL 2 OpenGL functions require a context current on the calling thread",
                cause,
            )
        }
    }
}

internal enum class Lwjgl2ShaderEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2BufferEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2MultitextureEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2VertexInputEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2BlendFunctionEntryPoints {
    Core,
    EXT,
}

internal enum class Lwjgl2BlendEquationEntryPoints {
    Core,
    EXT,
}

internal enum class Lwjgl2FramebufferEntryPoints {
    Core,
    ARB,
    EXT,
}

internal enum class Lwjgl2VertexArrayEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2UniformBufferEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2SamplerEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl2ColorClampingEntryPoints {
    Core,
    ARB,
}

internal fun ContextCapabilities.colorClampingEntryPoints(): Lwjgl2ColorClampingEntryPoints? {
    if (OpenGL32) {
        val profileMask = GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)
        val contextFlags = GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS)
        val isCompatibilityProfile = profileMask and GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT != 0
        val isForwardCompatible = contextFlags and GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT != 0
        if (!isCompatibilityProfile || isForwardCompatible) {
            return null
        }
    } else if (OpenGL31) {
        val contextFlags = GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS)
        val isForwardCompatible = contextFlags and GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT != 0
        if (isForwardCompatible || !GL_ARB_compatibility) {
            return null
        }
    } else if (OpenGL30) {
        val contextFlags = GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS)
        val isForwardCompatible = contextFlags and GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT != 0
        if (isForwardCompatible) {
            return null
        }
    }

    return when {
        OpenGL30 -> Lwjgl2ColorClampingEntryPoints.Core
        GL_ARB_color_buffer_float -> Lwjgl2ColorClampingEntryPoints.ARB
        else -> null
    }
}
