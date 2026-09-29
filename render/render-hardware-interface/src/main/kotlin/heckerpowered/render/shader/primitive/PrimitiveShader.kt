/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.primitive

import heckerpowered.render.pipeline.PipelineLayoutDescription
import heckerpowered.render.pipeline.PipelineLayoutDescriptionBuilder
import heckerpowered.render.pipeline.pipelineLayout
import heckerpowered.render.pipeline.vertex.VertexFormat.*
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.pipeline.vertex.vertexState
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.DescriptorType
import heckerpowered.render.shader.binding.descriptorSetLayout

/**
 * Primitive drawing operations with no implicit fog, lighting, alpha discard or blending.
 *
 * Vertex input uses the existing RHI layout: position at location 0, UV at 1, RGBA at 2.
 * Geometry UVs pass through unchanged; screen copies preserve the orientation of images
 * produced by the backend's own render targets.
 */
enum class PrimitiveShader(
    val vertexState: VertexState,
    val layoutDescription: PipelineLayoutDescription,
) {
    Position(
        vertexState(stride = 12) {
            attribute(location = 0, format = Float32x3, offset = 0)
        },
        standardPipelineLayout("Position") {
            pushConstants(GeometryConstantsLayout)
        },
    ),

    PositionColor(
        vertexState(stride = 28) {
            attribute(location = 0, format = Float32x3, offset = 0)
            attribute(location = 2, format = Float32x4, offset = 12)
        },
        standardPipelineLayout("PositionColor") {
            pushConstants(GeometryConstantsLayout)
        },
    ),

    PositionTexture(
        vertexState(stride = 20) {
            attribute(location = 0, format = Float32x3, offset = 0)
            attribute(location = 1, format = Float32x2, offset = 12)
        },
        standardPipelineLayout("PositionTexture") {
            descriptorSet(StandardTextureLayout)
            pushConstants(GeometryConstantsLayout)
        },
    ),

    PositionTextureColor(
        vertexState(stride = 36) {
            attribute(location = 0, format = Float32x3, offset = 0)
            attribute(location = 1, format = Float32x2, offset = 12)
            attribute(location = 2, format = Float32x4, offset = 20)
        },
        standardPipelineLayout("PositionTextureColor") {
            descriptorSet(StandardTextureLayout)
            pushConstants(GeometryConstantsLayout)
        },
    ),

    /**
     * Uses the device unit quad: `[0, 1] x [-1, 1]`, in RHI clip orientation.
     */
    BlitScreen(
        vertexState(stride = 8) {
            attribute(location = 0, format = Float32x2, offset = 0)
        },
        standardPipelineLayout("BlitScreen") {
            descriptorSet(StandardTextureLayout)
            pushConstants(ScreenConstantsLayout)
        },
    ),

    FillScreen(
        vertexState(stride = 8) {
            attribute(location = 0, format = Float32x2, offset = 0)
        },
        standardPipelineLayout("FillScreen") {
            pushConstants(ScreenConstantsLayout)
        },
    ),
}

val StandardTextureLayout = descriptorSetLayout("Standard texture") {
    binding(0, DescriptorType.CombinedTextureSampler(), ShaderStage.Fragment)
}

private fun standardPipelineLayout(shader: String, block: PipelineLayoutDescriptionBuilder.() -> Unit): PipelineLayoutDescription =
    pipelineLayout("Standard $shader interface", block)