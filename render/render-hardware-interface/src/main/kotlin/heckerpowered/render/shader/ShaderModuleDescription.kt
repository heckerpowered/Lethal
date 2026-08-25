/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Description used to create a stage-specific [ShaderModule].
 *
 * [code] provides the shader representation, while [stage] and [entryPoint] select the shader
 * entry point represented by the resulting module.
 *
 * [label] identifies the resulting logical GPU resource for diagnostics and debugging. It does
 * not affect shader compilation or execution.
 */
data class ShaderModuleDescription(
    /**
     * Shader stage for which the module is created.
     *
     * The selected entry point in [code] must be compatible with this stage.
     */
    val stage: ShaderStage,

    /**
     * Source or binary shader representation used to create the module.
     */
    val code: ShaderCode,

    /**
     * Name of the shader entry point selected from [code].
     *
     * Typical GLSL source uses `"main"`, while representations such as SPIR-V may contain
     * multiple named entry points.
     *
     * Whether arbitrary entry-point names are supported depends on the shader representation and
     * graphics backend.
     */
    val entryPoint: String = "main",

    /**
     * Human-readable name of the resulting shader module.
     *
     * By default, this inherits [ShaderCode.label]. The label does not need to be unique and is
     * used only for diagnostics, debugging, and backend object naming.
     */
    val label: String = code.label,
)