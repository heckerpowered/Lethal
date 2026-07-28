/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@PushConstantBlock(ShaderStage.Fragment, offsetBytes = 16)
internal interface TestPushConstants {
    val opacity: Float

    val sampleCount: Int
}

class PushConstantBlockTest {
    @Test
    fun disjointShaderStagesMayReuseTheSamePushConstantRange() {
        val vertex = PushConstantLayout(16, listOf(PushConstantField("vertexValue", ShaderValueType.Float4, 0, setOf(ShaderStage.Vertex))))
        val fragment = PushConstantLayout(4, listOf(PushConstantField("fragmentValue", ShaderValueType.Float, 0, setOf(ShaderStage.Fragment))))

        val combined = pushConstantLayout(vertex, fragment)

        assertEquals(expected = 16, actual = combined.sizeBytes)
        assertEquals(expected = vertex.fields + fragment.fields, actual = combined.fields)
    }

    @Test
    fun fieldsVisibleToTheSameStageMayNotOverlap() {
        assertFailsWith<IllegalArgumentException> {
            PushConstantLayout(16, listOf(PushConstantField("first", ShaderValueType.Float4, 0, setOf(ShaderStage.Vertex)), PushConstantField("second", ShaderValueType.Float, 0, setOf(ShaderStage.Vertex))))
        }
    }

    @Test
    fun generatedCommandWritesAndRecordsItsTypedRange() {
        val renderPass = CapturingRenderPass()

        renderPass.pushTestPushConstants {
            opacity = 0.25F
            sampleCount = 7
        }

        assertEquals(expected = CapturedPushConstants(0.25F, 7, setOf(ShaderStage.Fragment), 8, 16), actual = renderPass.captured)
        assertEquals(expected = 24, actual = TestPushConstantsLayout.sizeBytes)
        assertEquals(expected = listOf(PushConstantField("opacity", ShaderValueType.Float, 16, setOf(ShaderStage.Fragment)), PushConstantField("sampleCount", ShaderValueType.Int, 20, setOf(ShaderStage.Fragment))), actual = TestPushConstantsLayout.fields)
    }

    private class CapturingRenderPass : RenderPass {
        override val memoryStack = MemoryStack()
        override val primitives: BuiltInPrimitives
            get() = error("Test does not use built-in primitives")
        var captured: CapturedPushConstants? = null

        override fun bindProtocol(protocol: RenderProtocol) = Unit
        override fun bindPipeline(pipeline: RenderPipeline) = Unit
        override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Int) = Unit
        override fun bindDescriptorSet(set: Int, descriptors: DescriptorSet) = Unit

        override fun pushConstants(stages: Set<ShaderStage>, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int) {
            captured = CapturedPushConstants(memoryStack.loadFloat(sourceAddress), memoryStack.loadInt(sourceAddress + Float.SIZE_BYTES), stages, sizeBytes, destinationOffsetBytes)
        }

        override fun draw(vertexCount: Int, firstVertex: Int) = Unit
    }

    private data class CapturedPushConstants(
        val opacity: Float,
        val sampleCount: Int,
        val stages: Set<ShaderStage>,
        val sizeBytes: Int,
        val destinationOffsetBytes: Int,
    )
}
