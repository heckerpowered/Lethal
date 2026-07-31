/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.function.OpenGLFunctions
import heckerpowered.render.opengl.function.OpenGLFunctionsProvider
import org.lwjgl.opengl.*

/** Loads the thin LWJGL 3 adapter for the context current on the calling thread. */
class Lwjgl3OpenGLFunctionsProvider : OpenGLFunctionsProvider {
    override fun create(): OpenGLFunctions {
        val capabilities = currentCapabilities()
        val missingCapabilities = mutableListOf<String>()

        if (!capabilities.OpenGL11) {
            missingCapabilities += "OpenGL 1.1 state, texture, and drawing operations"
        }

        val shaderEntryPoints = when {
            capabilities.OpenGL20 -> Lwjgl3ShaderEntryPoints.Core
            capabilities.GL_ARB_shader_objects && capabilities.GL_ARB_vertex_shader && capabilities.GL_ARB_fragment_shader -> Lwjgl3ShaderEntryPoints.ARB
            else -> null
        }
        if (shaderEntryPoints == null) {
            missingCapabilities += "shader objects (OpenGL 2.0 or ARB shader, vertex-shader, and fragment-shader extensions)"
        }

        val bufferEntryPoints = when {
            capabilities.OpenGL15 -> Lwjgl3BufferEntryPoints.Core
            capabilities.GL_ARB_vertex_buffer_object -> Lwjgl3BufferEntryPoints.ARB
            else -> null
        }
        if (bufferEntryPoints == null) {
            missingCapabilities += "buffer objects (OpenGL 1.5 or ARB_vertex_buffer_object)"
        }

        val multitextureEntryPoints = when {
            capabilities.OpenGL13 -> Lwjgl3MultitextureEntryPoints.Core
            capabilities.GL_ARB_multitexture -> Lwjgl3MultitextureEntryPoints.ARB
            else -> null
        }
        if (multitextureEntryPoints == null) {
            missingCapabilities += "active texture selection (OpenGL 1.3 or ARB_multitexture)"
        }

        val vertexInputEntryPoints = when {
            capabilities.OpenGL20 -> Lwjgl3VertexInputEntryPoints.Core
            capabilities.GL_ARB_vertex_shader -> Lwjgl3VertexInputEntryPoints.ARB
            else -> null
        }
        if (vertexInputEntryPoints == null) {
            missingCapabilities += "generic vertex attributes (OpenGL 2.0 or ARB_vertex_shader)"
        }

        val blendFunctionEntryPoints = when {
            capabilities.OpenGL14 -> Lwjgl3BlendFunctionEntryPoints.Core
            capabilities.GL_EXT_blend_func_separate -> Lwjgl3BlendFunctionEntryPoints.EXT
            else -> null
        }
        if (blendFunctionEntryPoints == null) {
            missingCapabilities += "separate blend factors (OpenGL 1.4 or EXT_blend_func_separate)"
        }

        val blendEquationEntryPoints = when {
            capabilities.OpenGL14 -> Lwjgl3BlendEquationEntryPoints.Core
            capabilities.GL_EXT_blend_minmax && capabilities.GL_EXT_blend_subtract -> Lwjgl3BlendEquationEntryPoints.EXT
            else -> null
        }
        if (blendEquationEntryPoints == null) {
            missingCapabilities += "blend equations (OpenGL 1.4 or EXT_blend_minmax + EXT_blend_subtract)"
        }

        if (missingCapabilities.isNotEmpty()) {
            throw IllegalStateException("LWJGL 3 cannot create the mandatory OpenGL function baseline; missing ${missingCapabilities.joinToString()}")
        }

        val selectedBufferEntryPoints = requireNotNull(bufferEntryPoints)
        return Lwjgl3OpenGLFunctions(
            requireNotNull(shaderEntryPoints),
            selectedBufferEntryPoints,
            requireNotNull(multitextureEntryPoints),
            requireNotNull(vertexInputEntryPoints),
            requireNotNull(blendFunctionEntryPoints),
            requireNotNull(blendEquationEntryPoints),
            Lwjgl3FramebufferFunctions.create(capabilities),
            Lwjgl3VertexArrayFunctions.create(capabilities),
            Lwjgl3UniformBufferFunctions.create(capabilities, selectedBufferEntryPoints),
            Lwjgl3SamplerFunctions.create(capabilities),
            Lwjgl3ShaderColorClampingFunctions.create(capabilities)
        )
    }

    private fun currentCapabilities(): GLCapabilities {
        return try {
            GL.getCapabilities()
        } catch (cause: IllegalStateException) {
            throw IllegalStateException(
                "LWJGL 3 OpenGL functions require a context current on the calling thread",
                cause,
            )
        }
    }
}

internal enum class Lwjgl3ShaderEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3BufferEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3MultitextureEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3VertexInputEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3BlendFunctionEntryPoints {
    Core,
    EXT,
}

internal enum class Lwjgl3BlendEquationEntryPoints {
    Core,
    EXT,
}

internal enum class Lwjgl3FramebufferEntryPoints {
    Core,
    ARB,
    EXT,
}

internal enum class Lwjgl3VertexArrayEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3UniformBufferEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3SamplerEntryPoints {
    Core,
    ARB,
}

internal enum class Lwjgl3ColorClampingEntryPoints {
    Core,
    ARB,
}

internal fun GLCapabilities.colorClampingEntryPoints(): Lwjgl3ColorClampingEntryPoints? {
    val supported = when {
        OpenGL32 -> supportsColorClampingInOpenGL32()
        OpenGL31 -> supportsColorClampingInOpenGL31()
        OpenGL30 -> supportsColorClampingInOpenGL30()
        else -> true
    }
    if (!supported) {
        return null
    }

    return when {
        OpenGL30 -> Lwjgl3ColorClampingEntryPoints.Core
        GL_ARB_color_buffer_float -> Lwjgl3ColorClampingEntryPoints.ARB
        else -> null
    }
}

private fun isForwardCompatible(): Boolean =
    GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS) and GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT != 0

private fun supportsColorClampingInOpenGL32(): Boolean {
    val profileMask = GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)
    val isCompatibilityProfile = profileMask and GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT != 0
    return isCompatibilityProfile && !isForwardCompatible()
}

private fun GLCapabilities.supportsColorClampingInOpenGL31(): Boolean =
    !isForwardCompatible() && GL_ARB_compatibility

private fun supportsColorClampingInOpenGL30(): Boolean =
    !isForwardCompatible()