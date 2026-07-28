/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.*

/**
 * Exposes commands that may be appended to an active recording.
 */
interface CommandScope {
    val memoryStack: MemoryStack

    fun writeBuffer(buffer: GpuBuffer, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int = 0)

    /**
     * Copies one complete uniform block into storage owned by this command recording.
     *
     * The returned binding remains valid only until the recording closes.
     */
    fun writeUniform(layout: UniformBufferLayout, sourceAddress: NativeAddress): UniformBinding

    fun renderPass(description: RenderPassDescription, commands: RenderPass.() -> Unit)
}

/**
 * Owns one backend command recording.
 *
 * Closing the encoder completes the sequence. Implementations must make any submitted source buffer contents
 * independent of the caller before returning from [writeBuffer]. Rendering code may receive the narrower
 * [CommandScope] when it must not complete the recording itself.
 */
interface CommandEncoder : CommandScope, AutoCloseable

/**
 * Records commands within one render pass.
 */
interface RenderPass {
    val memoryStack: MemoryStack
    val primitives: BuiltInPrimitives

    fun bindProtocol(protocol: RenderProtocol)
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