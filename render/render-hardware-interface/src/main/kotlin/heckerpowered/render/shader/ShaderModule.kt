/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import heckerpowered.render.GpuResource

/**
 * Backend-compiled shader code for exactly one [ShaderStage].
 *
 * This RHI intentionally models shader modules as stage-specific resources. Backends whose
 * native shader modules may contain multiple stages or entry points must expose the selected
 * stage as a distinct logical [ShaderModule].
 */
interface ShaderModule : GpuResource {
    /**
     * Shader stage for which this module was compiled.
     */
    val stage: ShaderStage

    /**
     * Entry point selected when this module was created.
     */
    val entryPoint: String
}