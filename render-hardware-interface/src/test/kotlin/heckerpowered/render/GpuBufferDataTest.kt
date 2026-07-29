/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.*
import kotlin.test.Test
import kotlin.test.assertEquals

@GpuBufferData
internal interface TestBufferData {
    @FloatElements(1)
    val opacity: NativeAddress

    @IntElements(1)
    val sampleCount: NativeAddress
}

class GpuBufferDataTest {
    @Test
    fun generatedLayoutDescribesTheShaderFields() {
        assertEquals(expected = 16, actual = TestBufferDataLayout.sizeBytes)
        assertEquals(expected = listOf(ShaderField("opacity", ShaderValueType.Float, 0), ShaderField("sampleCount", ShaderValueType.Int, 4)), actual = TestBufferDataLayout.fields)
    }

    @Test
    fun generatedCommandWritesTheCompleteAlignedStructure() {
        val encoder = CapturingCommandEncoder()
        encoder.memoryStack.alloc(floats(4)) { address -> storeFloat4(address, 1.0F, 1.0F, 1.0F, 1.0F) }

        encoder.writeTestBufferData {
            opacity = 0.25F
            sampleCount = 7
        }

        assertEquals(expected = CapturedBufferData(0.25F, 7, 0.0F, 0.0F, 16, 0), actual = encoder.captured)
    }

    private class CapturingCommandEncoder : CommandEncoder {
        override val memoryStack = MemoryStack()
        var captured: CapturedBufferData? = null

        override fun writeBuffer(buffer: GpuBuffer, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int) = Unit

        override fun writeUniform(layout: UniformBufferLayout, sourceAddress: NativeAddress): UniformBinding {
            captured = CapturedBufferData(memoryStack.loadFloat(sourceAddress), memoryStack.loadInt(sourceAddress + Float.SIZE_BYTES), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES * 2), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES * 3), layout.sizeBytes, 0)
            return CapturedUniformBinding(layout)
        }

        override fun renderPass(description: RenderPassDescription, commands: RenderPass.() -> Unit) = Unit
    }

    private data class CapturedBufferData(
        val opacity: Float,
        val sampleCount: Int,
        val firstPaddingValue: Float,
        val secondPaddingValue: Float,
        val sizeBytes: Int,
        val destinationOffsetBytes: Int,
    )

    private data class CapturedUniformBinding(override val layout: UniformBufferLayout) : UniformBinding
}
