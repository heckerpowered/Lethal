/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.shader.parameter.ParameterDerivations

/**
 * Describes how a shader consumes geometry and named parameters.
 *
 * Vertex, descriptor, and push-constant declarations connect semantic inputs to concrete shader
 * locations and resource slots. They must agree with the compiled shader; reflection cannot infer meanings
 * such as position or a material's texture from a numeric location alone.
 *
 * The pass processor supplies values and uses [derivations] to compute requested numeric inputs.
 * Every consumed parameter must then be present; additional values are allowed. This layout
 * does not select a pass, blend equation, depth policy, or rasterization state.
 * It retains engine parameter mappings and derivation rules, beyond the descriptor slots and
 * push-constant ranges represented by an RHI pipeline layout.
 */
internal class ShaderInputLayout(
    val vertices: VertexInterface,
    descriptors: List<DescriptorInterface>,
    pushes: List<PushConstantInterface>,
    val derivations: ParameterDerivations = ParameterDerivations(),
) {
    val descriptors = descriptors.toList()
    val pushes = pushes.toList()
    val parameterNames = this.descriptors.flatMap { it.parameters }.map { it.name }.toSet() +
            this.pushes.flatMap { it.fields }.map { it.name }.toSet()
}
