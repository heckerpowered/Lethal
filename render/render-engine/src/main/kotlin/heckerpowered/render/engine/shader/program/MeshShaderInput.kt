/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.material.parameter.ParameterValues

/** Geometry and encoded numeric/resource values produced together by a shader's typed encoder. */
class MeshShaderInput(
    val geometry: RenderGeometry,
    val parameters: ParameterValues = ParameterValues(),
)
