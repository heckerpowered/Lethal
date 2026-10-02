/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.material.parameter.ParameterName

/**
 * Places one named numeric value inside a parameter block.
 *
 * [offsetBytes] is relative to the block, not the pipeline's push-constant address space. The
 * value must already have exactly [sizeBytes] bytes in the representation expected by the shader;
 * placing it does not perform numeric conversion or compute shader member alignment.
 */
data class PushConstantField(
    val name: ParameterName,
    val offsetBytes: Int,
    val sizeBytes: Int,
)
