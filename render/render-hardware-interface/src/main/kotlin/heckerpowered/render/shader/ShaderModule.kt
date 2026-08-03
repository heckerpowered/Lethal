/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import heckerpowered.render.GpuResource
import heckerpowered.render.ShaderStage

/**
 * Backend-compiled shader code for exactly one [heckerpowered.render.ShaderStage].
 *
 * A shader module is created from [heckerpowered.render.ShaderSource] by
 * [heckerpowered.render.GraphicsDevice.createShaderModule]. Creation performs any backend compilation required to
 * produce a module that can later be used to create a render pipeline.
 *
 * A module is not an executable graphics pipeline by itself. Pipeline creation combines it with
 * other shader stages and fixed-function state, and may perform additional backend linking or
 * validation.
 */
interface ShaderModule : GpuResource {
    val stage: ShaderStage
}