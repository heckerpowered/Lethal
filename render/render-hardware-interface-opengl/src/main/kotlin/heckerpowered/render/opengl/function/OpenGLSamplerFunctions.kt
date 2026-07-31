/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.SamplerName
import heckerpowered.render.opengl.TextureUnit

/**
 * Stores sampling parameters independently from texture image objects.
 *
 * Adapters may normalize `ARB_sampler_objects` entry points.
 */
interface OpenGLSamplerFunctions {
    fun createSampler(): SamplerName
    fun getBoundSampler(unit: TextureUnit): SamplerName
    fun bindSampler(unit: TextureUnit, sampler: SamplerName)
    fun samplerParameter(sampler: SamplerName, parameter: Int, value: Int)
    fun deleteSampler(sampler: SamplerName)
}