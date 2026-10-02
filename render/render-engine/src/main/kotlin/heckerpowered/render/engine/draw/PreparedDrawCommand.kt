/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.collection.toUnmodifiableList
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.IndexedRange
import heckerpowered.render.engine.geometry.VertexRange
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.shader.binding.DescriptorSet
import java.util.*

/**
 * One draw with its pipeline, resource bindings, and draw range already selected for a pass.
 *
 * Pass preparation produces this value so recording needs no knowledge of where geometry came
 * from or why a pipeline was selected:
 * [pipeline], the concrete vertex/index bindings, descriptor selections, push-constant bytes, and
 * [arguments] can be translated mechanically to RHI commands.
 *
 * The record is self-contained with respect to shader and geometry inputs consumed by its draw.
 * A lowerer must therefore supply every descriptor set and push-constant value read by the selected
 * shaders instead of relying on compatible state left by a previous draw. The public RHI does not
 * expose shader-use reflection strongly enough for this class to prove that completeness itself;
 * the backend still performs its ordinary draw-time validation.
 *
 * [viewport], [scissor], and [stencilReference] are resolved dynamic state. Preparation intersects
 * the submission's nested clips with the render area, leaving one effective clip for recording.
 * Attachment operations and pass selection remain properties of the prepared pass.
 *
 * Binding collections and push-constant bytes are snapshots. GPU resources are borrowed, not
 * copied or kept alive: the producer must keep them valid through recording and GPU completion.
 * Buffer bytes and texture contents still follow command ordering rather than this record's
 * construction time. The pipeline description must remain unchanged while this record is used.
 */
internal class PreparedDrawCommand(
    val pipeline: RenderPipelineDescription,
    vertexBuffers: Map<Int, GpuBufferView> = emptyMap(),
    val indexInput: IndexInput? = null,
    descriptorSets: Map<Int, DescriptorSet> = emptyMap(),
    pushConstants: List<PreparedPushConstantWrite> = emptyList(),
    val arguments: DrawRange,
    val viewport: Viewport,
    val scissor: ScissorRectangle,
    val stencilReference: UByte = 0u,
) {
    val vertexBuffers: Map<Int, GpuBufferView> = immutableSortedMap(vertexBuffers)
    val descriptorSets: Map<Int, DescriptorSet> = immutableSortedMap(descriptorSets)
    val pushConstants: List<PreparedPushConstantWrite> = pushConstants.toUnmodifiableList()

    init {
        validateVertexBindings()
        require(this.descriptorSets.keys.all { it >= 0 }) { "Descriptor set index must not be negative" }

        when (val draw = arguments) {
            is VertexRange -> {
                require(indexInput == null) { "A non-indexed prepared draw must not carry an index binding" }
                pipeline.vertex.validateDrawInputs(this.vertexBuffers, draw.vertexCount, draw.firstVertex, draw.instanceCount, draw.firstInstance)
            }

            is IndexedRange -> {
                val indices = checkNotNull(indexInput) { "An indexed prepared draw requires an index binding" }
                indices.format.validateDrawRange(indices.view, draw.indexCount, draw.firstIndex)
                pipeline.vertex.validateIndexedDrawInputs(this.vertexBuffers, draw.indexCount, draw.instanceCount, draw.firstInstance)
            }
        }
    }

    private fun validateVertexBindings() {
        for ([slot, view] in vertexBuffers) {
            require(slot in pipeline.vertex.buffers.indices) { "Vertex-buffer slot $slot is outside the selected pipeline vertex state" }
            val layout = pipeline.vertex.buffers[slot]
            require(layout.attributes.isNotEmpty()) { "Vertex-buffer slot $slot has no attributes in the selected pipeline vertex state" }
            layout.validateAccess(view, 0, 0, "Prepared vertex-buffer slot $slot")
        }
    }

    /**
     * Encodes this draw into an active pass whose resource declaration covers its bindings.
     * The caller enters the prepared pass without additional raster scopes. RHI scope operations
     * restore its viewport and clip after this command; stencil reference is selected by every draw.
     */
    fun encode(pass: RenderPass) {
        pass.withViewport(viewport) {
            withScissor(scissor) {
                setStencilReference(stencilReference)
                encodeDraw()
            }
        }
    }

    private fun RenderPass.encodeDraw() {
        bindPipeline(pipeline)
        vertexBuffers.forEach { [slot, view] -> bindVertexBuffer(slot, view) }
        indexInput?.let { bindIndexBuffer(it.view, it.format) }
        descriptorSets.forEach { [set, descriptors] -> bindDescriptorSet(set, descriptors) }

        pushConstants.forEach { write ->
            memoryStack.frame {
                val address = reserve(write.sizeBytes, 4)
                write.copyTo(asByteBuffer(address, write.sizeBytes))
                pushConstants(write.stages, address, write.sizeBytes, write.destinationOffsetBytes)
            }
        }

        when (val draw = arguments) {
            is VertexRange -> draw(draw.vertexCount, draw.firstVertex, draw.instanceCount, draw.firstInstance)
            is IndexedRange -> drawIndexed(draw.indexCount, draw.firstIndex, draw.baseVertex, draw.instanceCount, draw.firstInstance)
        }
    }
}

private fun <T> immutableSortedMap(values: Map<Int, T>): Map<Int, T> {
    val result = LinkedHashMap<Int, T>(values.size)
    values.toSortedMap().forEach { [slot, value] -> result[slot] = value }
    return Collections.unmodifiableMap(result)
}
