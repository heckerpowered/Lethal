/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import heckerpowered.render.GpuResource

/**
 * Represents shader code prepared for one selected [ShaderStage] and entry point.
 *
 * Creating a module selects which stage the code will execute in and which named entry point begins
 * that execution. A typical render pipeline uses a vertex module to process vertices and a fragment
 * module to determine the results written for rasterized fragments.
 *
 * A module may be reused in multiple [ShaderStages] combinations. For example, one vertex module
 * that transforms mesh vertices may be paired with several fragment modules that produce different
 * surface effects.
 *
 * Successful creation means that the selected stage and entry point were accepted in isolation. It
 * does not establish compatibility with another shader module or with a complete render pipeline.
 * For example, a vertex module may be created successfully even if its outputs do not match the
 * inputs expected by a fragment module. Stage-to-stage compatibility is established when
 * [ShaderStages] is created, while pipeline-specific compatibility is established during
 * render-pipeline creation.
 *
 * A [ShaderStages] borrows its constituent modules rather than taking ownership of them. This
 * module must therefore remain open while any established stage combination still uses it.
 *
 * A [ShaderModule] always represents one fixed stage and entry point, even when the original
 * [ShaderCode] can provide several entry points or be used for different stages. Selecting another
 * stage or entry point requires creating another module.
 */
interface ShaderModule : GpuResource {
    /**
     * Stage selected when this module was created.
     *
     * This value cannot be changed or overridden when the module is added to [ShaderStages].
     */
    val stage: ShaderStage

    /**
     * Entry point selected when this module was created.
     *
     * This value cannot be changed or overridden when the module is added to [ShaderStages].
     */
    val entryPoint: String
}