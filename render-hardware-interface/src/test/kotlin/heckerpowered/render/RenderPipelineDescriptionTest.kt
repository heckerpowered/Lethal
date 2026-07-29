/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderPipelineDescriptionTest {
    @Test
    fun renderPipelineDslPreservesExplicitDescriptorSetIndices() {
        val scene = descriptorSetLayout { uniformBuffer(0, "Scene", uniformBufferLayout(4) { float("value", 0) }, ShaderStage.Vertex) }
        val style = descriptorSetLayout { combinedImageSampler(0, "texture", ShaderStage.Fragment) }

        val pipelineDescription = pipelineDescription {
            descriptorSet(0, scene)
            descriptorSet(1, style)
        }

        assertEquals(expected = listOf(scene, style), actual = pipelineDescription.descriptorSetLayouts)
    }

    @Test
    fun renderPipelineDslRejectsDescriptorSetGaps() {
        val descriptors = descriptorSetLayout { combinedImageSampler(0, "texture", ShaderStage.Fragment) }
        assertFailsWith<IllegalArgumentException> { pipelineDescription { descriptorSet(1, descriptors) } }
    }

    @Test
    fun renderPipelineDslUsesInertDefaults() {
        val pipelineDescription = renderPipeline("Default pipeline") { shaders(ShaderProgram(variant("fallback"))) }

        assertEquals(expected = VertexBufferLayout.Empty, actual = pipelineDescription.vertexBufferLayout)
        assertEquals(expected = PrimitiveTopology.TriangleList, actual = pipelineDescription.primitiveTopology)
        assertEquals(expected = BlendState.Disabled, actual = pipelineDescription.blendState)
        assertEquals(expected = DepthState.Disabled, actual = pipelineDescription.depthState)
        assertEquals(expected = CullMode.None, actual = pipelineDescription.cullMode)
        assertEquals(expected = emptyList(), actual = pipelineDescription.descriptorSetLayouts)
        assertEquals(expected = null, actual = pipelineDescription.pushConstants)
    }

    private fun pipelineDescription(descriptors: RenderPipelineBuilder.() -> Unit): RenderPipelineDescription {
        return renderPipeline("Test pipeline") {
            shaders(ShaderProgram(variant("fallback")))
            descriptors()
        }
    }

    private fun variant(@Suppress("SameParameterValue") label: String): ShaderProgramVariant = ShaderProgramVariant(ShaderSource.text("void main() {}", label = "$label vertex"), ShaderSource.text("void main() {}", label = "$label fragment"))
}
