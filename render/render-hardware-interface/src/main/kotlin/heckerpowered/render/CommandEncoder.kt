/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.*

/**
 * Encodes commands into the active graphics recording.
 *
 * A command encoder is valid only for the scoped recording that supplies it. The graphics device owns that recording's
 * completion and cleanup; callers neither close the encoder nor retain frame-scoped values created through it.
 */
interface CommandEncoder {
    val memoryStack: MemoryStack

    fun writeBuffer(buffer: GpuBuffer, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int = 0)

    /**
     * Copies one complete uniform block into storage owned by this command recording.
     *
     * The returned binding remains valid only until the current recording ends.
     */
    fun writeUniform(layout: UniformBufferLayout, sourceAddress: NativeAddress): UniformBinding

    fun renderPass(description: RenderPassDescription, commands: RenderPass.() -> Unit)
}

/**
 * Records commands within one render pass.
 */
interface RenderPass {
    val memoryStack: MemoryStack
    val primitives: BuiltInPrimitives

    fun bindPipeline(description: RenderPipelineDescription)
    fun bindPipeline(pipeline: RenderPipeline)
    fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Int = 0)
    fun bindDescriptorSet(set: Int, descriptors: DescriptorSet)
    fun pushConstants(stages: Set<ShaderStage>, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int = 0)
    fun draw(vertexCount: Int, firstVertex: Int = 0)
}

inline fun RenderPass.pushConstants(stages: Set<ShaderStage>, sizeBytes: Int, destinationOffsetBytes: Int = 0, write: MemoryFrame.(address: NativeAddress) -> Unit) {
    memoryStack.alloc(bytes(sizeBytes)) { address ->
        write(address)
        pushConstants(stages, address, sizeBytes, destinationOffsetBytes)
    }
}
