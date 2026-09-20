/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.BuiltInPrimitives
import heckerpowered.render.DescriptorSet
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.buffer.GpuBuffer
import heckerpowered.render.memory.*
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.pipeline.depthstencil.StencilOperation
import heckerpowered.render.shader.ShaderStage

/**
 * Records draws and the state they use within one logical render pass.
 *
 * [RenderPassDescription] selects the attachments and their load and store operations. Pipelines
 * and resource bindings can change between draws, allowing several objects with different
 * materials to contribute to the same attachments without beginning a new pass.
 *
 * This interface is supplied to the pass-recording callback and is valid only during that callback.
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

    /**
     * Sets the stencil reference value used by subsequent draws in this render pass.
     *
     * The reference is shared by front- and back-facing primitives. It initially equals zero,
     * remains active until changed again, and is not reset when another pipeline is bound.
     *
     * Stencil comparison masks the reference and stored stencil value independently using the
     * pipeline's read mask. [StencilOperation.Replace] instead uses the unmasked reference as its
     * operation result; the write mask then selects which result bits are stored.
     *
     * The value is ignored by pipelines that do not enable stencil testing.
     */
    fun setStencilReference(reference: UByte)
}

inline fun RenderPass.pushConstants(
    stages: Set<ShaderStage>,
    sizeBytes: Int,
    destinationOffsetBytes: Int = 0,
    write: MemoryFrame.(address: NativeAddress) -> Unit,
) {
    memoryStack.alloc(bytes(sizeBytes)) { address ->
        write(address)
        pushConstants(stages, address, sizeBytes, destinationOffsetBytes)
    }
}
