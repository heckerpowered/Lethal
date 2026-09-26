/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.BuiltInPrimitives
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.*
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.DescriptorSet

/** Attachment operations are native; drawing is deliberately unavailable in this first milestone. */
internal class OpenGLClearPass(
    private val encoder: OpenGLCommandEncoder,
    description: RenderPassDescription,
    val attachment: OpenGLRenderAttachment,
) : RenderPass {
    private val regions = RenderPassRegions(description.renderArea, ::checkActive)

    fun checkActive() {
        encoder.checkActive()
        check(encoder.currentPass === this) { "Render pass is outside its callback" }
        attachment.texture.checkOpen()
    }

    override val memoryStack: MemoryStack
        get() {
            checkActive()
            return encoder.device.memoryStack
        }
    override val primitives: BuiltInPrimitives get() = TODO("Implement OpenGL built-in geometry")

    override fun <R> withViewport(viewport: Viewport, commands: RenderPass.() -> R): R =
        regions.withViewport(viewport) { commands(this) }

    override fun <R> withScissor(rectangle: ScissorRectangle, commands: RenderPass.() -> R): R =
        regions.withScissor(rectangle) { commands(this) }

    override fun bindPipeline(description: RenderPipelineDescription): Unit = TODO("Implement OpenGL pipeline binding")
    override fun bindPipeline(pipeline: RenderPipeline): Unit = TODO("Implement OpenGL pipeline binding")
    override fun bindVertexBuffer(slot: Int, view: GpuBufferView): Unit = TODO("Implement OpenGL vertex binding")
    override fun bindIndexBuffer(view: GpuBufferView, format: IndexFormat): Unit = TODO("Implement OpenGL index binding")
    override fun bindDescriptorSet(set: Int, descriptors: DescriptorSet): Unit = TODO("Implement OpenGL descriptor binding")
    override fun pushConstants(stages: Set<ShaderStage>, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int): Unit = TODO("Implement OpenGL push constants")
    override fun draw(vertexCount: Int, firstVertex: Int, instanceCount: Int, firstInstance: Int): Unit = TODO("Implement OpenGL draw")
    override fun drawIndexed(indexCount: Int, firstIndex: Int, baseVertex: Int, instanceCount: Int, firstInstance: Int): Unit = TODO("Implement OpenGL indexed draw")
    override fun setStencilReference(reference: UByte): Unit = TODO("Implement OpenGL stencil state")
}
