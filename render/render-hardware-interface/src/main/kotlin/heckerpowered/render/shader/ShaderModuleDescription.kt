/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Selects the shader code, stage, and entry point used to create one [ShaderModule].
 *
 * [stage] determines where the resulting module executes in the graphics pipeline, while
 * [entryPoint] selects the function at which execution begins. A single [ShaderCode] may be used
 * to create several modules when it provides entry points for different stages or purposes, but
 * each resulting module represents only the stage and entry point selected here.
 */
data class ShaderModuleDescription(
    /**
     * Pipeline stage for which the module is created.
     *
     * The selected entry point must be compatible with this stage. When the shader representation
     * declares its entry-point stage explicitly, that declaration must agree with this value.
     */
    val stage: ShaderStage,
    val code: ShaderCode,

    /**
     * Name of the entry point selected from [code].
     *
     * `"main"` is the conventional default. Some shader representations expose only a fixed entry
     * point and do not support selecting another name.
     */
    val entryPoint: String = "main",

    /**
     * Human-readable name of the resulting shader module.
     *
     * By default, this inherits [ShaderCode.label]. The label does not need to be unique and is
     * used only for diagnostics, debugging, and device object naming.
     */
    val label: String = code.label,
)