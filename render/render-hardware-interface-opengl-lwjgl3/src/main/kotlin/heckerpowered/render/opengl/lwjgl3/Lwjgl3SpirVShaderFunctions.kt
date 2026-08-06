/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.ShaderName
import heckerpowered.render.opengl.function.OpenGLSpirVShaderFunctions
import org.lwjgl.opengl.ARBGLSPIRV
import org.lwjgl.opengl.GL41
import org.lwjgl.opengl.GL46
import org.lwjgl.opengl.GLCapabilities
import java.nio.ByteBuffer

internal class Lwjgl3SpirVShaderFunctions private constructor(
    private val entryPoints: Lwjgl3SpirVShaderEntryPoints,
) : OpenGLSpirVShaderFunctions {
    override fun shaderBinary(shader: ShaderName, binary: ByteBuffer) {
        val binaryFormat = when (entryPoints) {
            Lwjgl3SpirVShaderEntryPoints.Core -> GL46.GL_SHADER_BINARY_FORMAT_SPIR_V
            Lwjgl3SpirVShaderEntryPoints.ARB -> ARBGLSPIRV.GL_SHADER_BINARY_FORMAT_SPIR_V_ARB
        }
        GL41.glShaderBinary(intArrayOf(shader.value), binaryFormat, binary)
    }

    override fun specializeShader(shader: ShaderName, entryPoint: String) = when (entryPoints) {
        Lwjgl3SpirVShaderEntryPoints.Core -> GL46.glSpecializeShader(shader.value, entryPoint, NoSpecializationConstants, NoSpecializationConstants)
        Lwjgl3SpirVShaderEntryPoints.ARB -> ARBGLSPIRV.glSpecializeShaderARB(shader.value, entryPoint, NoSpecializationConstants, NoSpecializationConstants)
    }

    companion object {
        private val NoSpecializationConstants = IntArray(0)

        fun create(capabilities: GLCapabilities): OpenGLSpirVShaderFunctions? {
            if (capabilities.glShaderBinary == 0L) return null

            val entryPoints = when {
                capabilities.OpenGL46 -> Lwjgl3SpirVShaderEntryPoints.Core
                capabilities.GL_ARB_gl_spirv -> Lwjgl3SpirVShaderEntryPoints.ARB
                else -> return null
            }
            return Lwjgl3SpirVShaderFunctions(entryPoints)
        }
    }
}
