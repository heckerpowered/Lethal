/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterName

/**
 * Connects a named parameter to one binding and array element in a descriptor set.
 *
 * [binding] is the layout's explicit binding number, and [element] selects an element of that
 * binding's fixed array. They do not depend on the position of this declaration in a list.
 *
 * [numericSizeBytes] must specify the exact byte size when numeric values supply a uniform buffer.
 * Existing buffer ranges are checked against the descriptor layout instead; this value
 * does not impose a separate size on them.
 */
class DescriptorParameter(
    val name: ParameterName,
    val binding: Int,
    val numericSizeBytes: Int? = null,
    val element: Int = 0,
    val alphaQuantity: AlphaQuantity? = null,
    val representation: AlphaRepresentation? = null,
)
