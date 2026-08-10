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

    /** Returns the sampler bound to the currently active texture unit. */
    fun getBoundSampler(): SamplerName

    fun bindSampler(unit: TextureUnit, sampler: SamplerName)
    fun samplerParameter(sampler: SamplerName, parameter: Int, value: Int)
    fun deleteSampler(sampler: SamplerName)
}

context(function: OpenGLSamplerFunctions)
fun createSampler(): SamplerName = function.createSampler()

context(function: OpenGLSamplerFunctions)
fun getBoundSampler(): SamplerName = function.getBoundSampler()

context(function: OpenGLSamplerFunctions)
fun bindSampler(unit: TextureUnit, sampler: SamplerName) = function.bindSampler(unit, sampler)

context(function: OpenGLSamplerFunctions)
fun samplerParameter(sampler: SamplerName, parameter: Int, value: Int) =
    function.samplerParameter(sampler, parameter, value)

context(function: OpenGLSamplerFunctions)
fun deleteSampler(sampler: SamplerName) = function.deleteSampler(sampler)
