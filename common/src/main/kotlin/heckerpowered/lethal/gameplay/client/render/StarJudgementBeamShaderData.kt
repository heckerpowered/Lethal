/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.render.*
import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.NativeAddress

@GpuBufferData
internal interface StarJudgementBeamScene {
    @FloatElements(16)
    val modelViewProjectionMatrix: NativeAddress
}

@PushConstantBlock(ShaderStage.Vertex)
internal interface StarJudgementBeamFaceConstants {
    val beamOrigin: Float3

    val firstCorner: Float2

    val secondCorner: Float2

    val verticalRange: Float2

    val textureVRange: Float2

    val color: Float4
}
