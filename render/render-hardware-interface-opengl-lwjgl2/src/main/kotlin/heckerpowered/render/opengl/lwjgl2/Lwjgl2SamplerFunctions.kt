/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.opengl.SamplerName
import heckerpowered.render.opengl.TextureUnit
import heckerpowered.render.opengl.function.OpenGLSamplerFunctions
import org.lwjgl.opengl.ARBSamplerObjects
import org.lwjgl.opengl.ContextCapabilities
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL33

internal class Lwjgl2SamplerFunctions private constructor(
    private val entryPoints: Lwjgl2SamplerEntryPoints,
) : OpenGLSamplerFunctions {
    override fun createSampler(): SamplerName {
        val name = when (entryPoints) {
            Lwjgl2SamplerEntryPoints.Core -> GL33.glGenSamplers()
            Lwjgl2SamplerEntryPoints.ARB -> ARBSamplerObjects.glGenSamplers()
        }
        return SamplerName(name)
    }

    override fun getBoundSampler(): SamplerName {
        val binding = when (entryPoints) {
            Lwjgl2SamplerEntryPoints.Core -> GL33.GL_SAMPLER_BINDING
            Lwjgl2SamplerEntryPoints.ARB -> ARBSamplerObjects.GL_SAMPLER_BINDING
        }
        return SamplerName(GL11.glGetInteger(binding))
    }

    override fun bindSampler(unit: TextureUnit, sampler: SamplerName) = when (entryPoints) {
        Lwjgl2SamplerEntryPoints.Core -> GL33.glBindSampler(unit.value, sampler.value)
        Lwjgl2SamplerEntryPoints.ARB -> ARBSamplerObjects.glBindSampler(unit.value, sampler.value)
    }

    override fun samplerParameter(sampler: SamplerName, parameter: Int, value: Int) = when (entryPoints) {
        Lwjgl2SamplerEntryPoints.Core -> GL33.glSamplerParameteri(sampler.value, parameter, value)
        Lwjgl2SamplerEntryPoints.ARB -> ARBSamplerObjects.glSamplerParameteri(sampler.value, parameter, value)
    }

    override fun deleteSampler(sampler: SamplerName) = when (entryPoints) {
        Lwjgl2SamplerEntryPoints.Core -> GL33.glDeleteSamplers(sampler.value)
        Lwjgl2SamplerEntryPoints.ARB -> ARBSamplerObjects.glDeleteSamplers(sampler.value)
    }

    companion object {
        fun create(capabilities: ContextCapabilities): OpenGLSamplerFunctions? {
            val entryPoints = when {
                capabilities.OpenGL33 -> Lwjgl2SamplerEntryPoints.Core
                capabilities.GL_ARB_sampler_objects -> Lwjgl2SamplerEntryPoints.ARB
                else -> return null
            }
            return Lwjgl2SamplerFunctions(entryPoints)
        }
    }
}
