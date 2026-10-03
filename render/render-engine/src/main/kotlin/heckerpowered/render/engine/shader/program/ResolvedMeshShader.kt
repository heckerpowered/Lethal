/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.shader.binding.DescriptorInputMapping
import heckerpowered.render.engine.shader.binding.PushConstantPacking
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.shader.ShaderBinary
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import java.util.*

/** Accepted stage code and its corresponding CPU input mapping, before device objects are created. */
internal class ResolvedMeshShader(modules: List<ShaderModuleDescription>, inputs: ShaderInputLayout) {
    val modules = modules.map { description ->
        val code = when (val supplied = description.code) {
            is ShaderSource -> supplied.copy()
            is ShaderBinary -> ShaderBinary.copyOf(supplied.bytes, supplied.format, supplied.label)
        }
        description.copy(code = code)
    }.toUnmodifiableList()

    val inputs = ShaderInputLayout(
        VertexInputMapping(inputs.vertices.inputs),
        inputs.descriptors.map { DescriptorInputMapping(it.layout, it.parameters) },
        inputs.pushes.map { PushConstantPacking(Collections.unmodifiableSet(it.stages.toSet()), it.offsetBytes, it.sizeBytes, it.fields) },
        inputs.derivations,
    )

    init {
        require(this.modules.any { it.stage == ShaderStage.Vertex }) { "A mesh shader requires a vertex stage" }
        require(this.modules.any { it.stage == ShaderStage.Fragment }) { "A mesh shader requires a fragment stage" }
        require(this.modules.map { it.stage }.distinct().size == this.modules.size) { "Shader stages must be unique" }
    }
}
