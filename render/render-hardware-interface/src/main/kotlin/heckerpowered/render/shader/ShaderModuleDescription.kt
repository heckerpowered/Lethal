/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import heckerpowered.render.ShaderSource

/**
 * Description used to create a stage-specific [ShaderModule].
 *
 * [code] supplies the shader representation, while [stage] defines the single pipeline stage for
 * which the resulting module is created. The supplied code must contain a compatible entry point
 * and otherwise satisfy the requirements of the selected stage.
 *
 * [label] identifies the resulting logical GPU resource. By default, it inherits the diagnostic
 * label of [code], but callers may override it when creating multiple modules or variants from the
 * same code object.
 */
data class ShaderModuleDescription(
    /**
     * Shader stage for which the module is created.
     */
    val stage: ShaderStage,

    /**
     * Source or binary shader representation used to create the module.
     */
    val source: ShaderSource,

    /**
     * Name of the shader entry point selected from [code].
     *
     * The entry point must be compatible with [stage]. Typical GLSL source uses `"main"`, while
     * binary representations such as SPIR-V may contain multiple named entry points.
     */
    val entryPoint: String = "main",

    /**
     * Human-readable name of the resulting shader module.
     *
     * The label is used only for diagnostics, debugging, and backend object naming.
     */
    val label: String = source.label,
)