/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.scene.GeometryElement

/**
 * Converts business values into geometry and named parameters for a fixed shader definition.
 * The encoder must produce inputs matching that definition's layout.
 */
class MeshShader<P>(
    val definition: ShaderDefinition,
    private val encode: (P) -> MeshShaderInput,
) {
    val label: String get() = definition.label
    val outputs: Map<Int, FragmentOutput> get() = definition.outputs
    val replaySafe: Boolean get() = definition.replaySafe

    val sourceRepresentation: AlphaRepresentation get() = definition.sourceRepresentation

    /** Encodes immediately; referenced GPU resources remain borrowed through GPU completion. */
    fun bind(values: P): GeometryElement {
        val input = encode(values)
        return GeometryElement(MeshShading(this, input.geometry, input.parameters))
    }

    internal fun resolve(device: GraphicsDevice): ResolvedMeshShader = definition.resolve(device)
}

/** Retains one encoded draw; referenced GPU resources remain borrowed through completion. */
class MeshShading internal constructor(
    val shader: MeshShader<*>,
    val geometry: RenderGeometry,
    internal val parameters: ParameterValues,
)
