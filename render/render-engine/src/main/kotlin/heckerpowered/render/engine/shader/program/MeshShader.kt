/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.shader.binding.DescriptorInputMapping
import heckerpowered.render.engine.shader.binding.PushConstantPacking
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.engine.shader.parameter.ParameterDerivations
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.engine.support.collection.toUnmodifiableMap
import heckerpowered.render.shader.ShaderBinary
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderStage
import java.util.*

/**
 * Associates raster shader code and its input contract with the encoder for [P].
 *
 * This is a CPU definition. Each engine creates and owns its device implementation on first use;
 * reuse the definition across draws and engines. Code and input declarations are captured at
 * construction. Labels are diagnostic and need not be unique. Replacing the definition selects
 * different code; an existing definition is not hot-reloaded.
 *
 * [bind] encodes immediately rather than retaining a mutable P. The encoder must produce values
 * and geometry matching the fixed contract. Resource values remain borrowed through GPU completion.
 * The typed input does not prove that the declared byte layout agrees with the compiled shader.
 * [outputs] and [sourceRepresentation] are producer contracts, not shader reflection or pixel checks.
 * Each fragment location declares its association and alpha quantity. [replaySafe] is also a caller
 * declaration: the producer must ensure repeated draws have no unwanted resource side effects.
 */
class MeshShader<P>(
    modules: List<ShaderModuleDescription>,
    vertices: VertexInputMapping,
    descriptors: List<DescriptorInputMapping> = emptyList(),
    pushes: List<PushConstantPacking> = emptyList(),
    derivations: ParameterDerivations = ParameterDerivations(),
    val sourceRepresentation: AlphaRepresentation = AlphaRepresentation.Straight,
    outputs: Map<Int, FragmentOutput> = mapOf(0 to FragmentOutput(sourceRepresentation)),
    val replaySafe: Boolean = false,
    val label: String,
    private val encode: (P) -> MeshShaderInput,
) {
    val outputs: Map<Int, FragmentOutput> = outputs.toUnmodifiableMap()

    internal val modules = modules.map { description ->
        val code = when (val supplied = description.code) {
            is ShaderSource -> supplied.copy()
            is ShaderBinary -> ShaderBinary.copyOf(supplied.bytes, supplied.format, supplied.label)
        }
        description.copy(code = code)
    }.toUnmodifiableList()

    internal val inputs = ShaderInputLayout(
        VertexInputMapping(vertices.inputs),
        descriptors.map { DescriptorInputMapping(it.layout, it.parameters) },
        pushes.map { PushConstantPacking(Collections.unmodifiableSet(it.stages.toSet()), it.offsetBytes, it.sizeBytes, it.fields) },
        derivations,
    )

    init {
        require(this.outputs.keys.all { it >= 0 }) { "Fragment locations must be nonnegative" }
        require(this.modules.any { it.stage == ShaderStage.Vertex }) { "A mesh shader requires a vertex stage" }
        require(this.modules.any { it.stage == ShaderStage.Fragment }) { "A mesh shader requires a fragment stage" }
        require(this.modules.map { it.stage }.distinct().size == this.modules.size) { "Shader stages must be unique" }
    }

    fun bind(values: P): GeometryElement {
        val input = encode(values)
        return GeometryElement(MeshShading(this, input.geometry, input.parameters))
    }
}

/**
 * Retains the shader, geometry and encoded values created by one typed binding.
 * GPU resources referenced by those values remain borrowed; this binding does not extend their lifetime.
 */
class MeshShading internal constructor(
    val shader: MeshShader<*>,
    val geometry: RenderGeometry,
    internal val parameters: ParameterValues,
)
