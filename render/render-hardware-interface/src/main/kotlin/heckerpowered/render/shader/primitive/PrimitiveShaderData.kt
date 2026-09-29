/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.primitive

import heckerpowered.render.Float4
import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.data.PushConstantBlock

/** World/model-to-clip matrix followed by a linear RGBA multiplier. Initialize both every draw. */
@PushConstantBlock(ShaderStage.Vertex, ShaderStage.Fragment)
interface GeometryConstants {
    @FloatElements(16)
    val transform: NativeAddress
    val color: Float4
}

/** Linear RGBA multiplier for blitting, or the output color for screen filling. */
@PushConstantBlock(ShaderStage.Fragment)
interface ScreenConstants {
    val color: Float4
}
