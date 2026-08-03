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
    val stage: ShaderStage,
    val source: ShaderSource,
    val label: String = source.label,
)