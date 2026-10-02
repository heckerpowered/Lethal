/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterName

/**
 * Describes one fragment output's value meaning, indexed by the shader's declared location.
 * Representation describes emitted RGB; alphaFromTexture derives alpha quantity from a selected
 * texture parameter for copy/filter shaders. These declarations do not choose attachment formats,
 * blend equations or write masks and do not prove the shader ABI or pixel values by reflection.
 */
data class FragmentOutput(
    val representation: AlphaRepresentation = AlphaRepresentation.Straight,
    val alphaQuantity: AlphaQuantity = AlphaQuantity.Coverage,
    val alphaFromTexture: ParameterName? = null,
)
