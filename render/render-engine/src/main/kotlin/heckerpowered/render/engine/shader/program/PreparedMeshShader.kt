/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.shader.ShaderStages

/**
 * Holds the device shader stages and pipeline layout prepared for a mesh shader.
 *
 * [layout] declares descriptor and push-constant ranges, while [inputs] connects those ranges and
 * vertex locations to rendering semantics. Draw processors combine these resources with
 * attachment formats, blending, depth behavior, rasterization, and pass selection to prepare
 * complete pipelines and draw commands.
 *
 * [sourceRepresentation] describes the fragment shader's output for alpha compositing.
 * [replaySafe] declares that repeating the shader does not introduce unwanted side effects; it
 * does not infer safety from the compiled code. Stages, their modules, and the pipeline layout
 * must remain valid through every GPU use. This value does not extend their resource lifetime.
 */
internal class PreparedMeshShader(
    val stages: ShaderStages,
    val layout: PipelineLayout?,
    val inputs: ShaderInputLayout,
    val sourceRepresentation: AlphaRepresentation = AlphaRepresentation.Straight,
    val replaySafe: Boolean = false,
)
