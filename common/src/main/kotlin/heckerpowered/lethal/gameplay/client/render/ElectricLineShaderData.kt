/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.bridge.math.VectorView
import heckerpowered.render.Color
import heckerpowered.render.Float3
import heckerpowered.render.Float4
import heckerpowered.render.GpuBufferData
import heckerpowered.render.PushConstantBlock
import heckerpowered.render.ShaderStage
import heckerpowered.render.memory.FloatElements
import heckerpowered.render.memory.MemoryFrame
import heckerpowered.render.memory.NativeAddress

@PushConstantBlock(ShaderStage.Fragment)
internal interface ElectricLineAnimationConstants {
    val animationTimeSeconds: Float
}

@PushConstantBlock(ShaderStage.Vertex)
internal interface ElectricLineDrawConstants {
    val startPosition: Float3

    val endPosition: Float3

    val lineColor: Float4
}

@GpuBufferData
internal interface ElectricLineScene {
    @FloatElements(16)
    val modelViewProjectionMatrix: NativeAddress

    @FloatElements(2)
    val viewportSize: NativeAddress
}

@GpuBufferData
internal interface ElectricLineStyle {
    @FloatElements(1)
    val coreHalfWidthPixels: NativeAddress

    @FloatElements(1)
    val ribbonHalfWidthPixels: NativeAddress

    @FloatElements(1)
    val spikeReachPixels: NativeAddress

    @FloatElements(1)
    val lightningIntensity: NativeAddress

    @FloatElements(1)
    val lightningSpikeDensity: NativeAddress

    @FloatElements(1)
    val lightningAnimationFrequency: NativeAddress
}

context(memoryFrame: MemoryFrame)
internal fun ElectricLineDrawConstantsWriter.startPosition(position: VectorView, origin: VectorView) {
    startPosition((position.x - origin.x).toFloat(), (position.y - origin.y).toFloat(), (position.z - origin.z).toFloat())
}

context(memoryFrame: MemoryFrame)
internal fun ElectricLineDrawConstantsWriter.endPosition(position: VectorView, origin: VectorView) {
    endPosition((position.x - origin.x).toFloat(), (position.y - origin.y).toFloat(), (position.z - origin.z).toFloat())
}

context(memoryFrame: MemoryFrame)
internal fun ElectricLineDrawConstantsWriter.lineColor(color: Color) {
    lineColor(color.red, color.green, color.blue, color.alpha)
}
