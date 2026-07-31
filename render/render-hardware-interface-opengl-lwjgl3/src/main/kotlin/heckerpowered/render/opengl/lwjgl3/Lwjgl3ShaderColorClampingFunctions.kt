/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.ColorClampMode
import heckerpowered.render.opengl.function.OpenGLShaderColorClampingFunctions
import org.lwjgl.opengl.ARBColorBufferFloat
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLCapabilities

internal class Lwjgl3ShaderColorClampingFunctions private constructor(
    private val entryPoints: Lwjgl3ColorClampingEntryPoints,
) : OpenGLShaderColorClampingFunctions {
    override fun clampVertexColor(mode: ColorClampMode) {
        clampColor(ARBColorBufferFloat.GL_CLAMP_VERTEX_COLOR_ARB, mode)
    }

    override fun clampFragmentColor(mode: ColorClampMode) {
        clampColor(ARBColorBufferFloat.GL_CLAMP_FRAGMENT_COLOR_ARB, mode)
    }

    private fun clampColor(target: Int, mode: ColorClampMode) = when (entryPoints) {
        Lwjgl3ColorClampingEntryPoints.Core -> GL30.glClampColor(target, mode.toOpenGL())
        Lwjgl3ColorClampingEntryPoints.ARB -> ARBColorBufferFloat.glClampColorARB(target, mode.toOpenGL())
    }

    companion object {
        fun create(capabilities: GLCapabilities): OpenGLShaderColorClampingFunctions? {
            val entryPoints = capabilities.colorClampingEntryPoints() ?: return null
            return Lwjgl3ShaderColorClampingFunctions(entryPoints)
        }
    }
}
