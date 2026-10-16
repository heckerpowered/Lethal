/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.engine.geometry.*
import heckerpowered.render.engine.geometry.index.IndexSelection
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.resource.buffer.GpuBufferView

/** A concrete draw invocation; indexed ranges always retain their prepared GPU index selection. */
internal sealed interface DrawInvocation {
    val range: DrawRange
    val indexBuffer: GpuBufferView?

    fun validate(vertex: VertexState, vertexBuffers: Map<Int, GpuBufferView>)
    fun bindIndexBuffer(pass: RenderPass)
    fun encode(pass: RenderPass)

    data class Vertices(override val range: VertexRange) : DrawInvocation {
        override val indexBuffer: GpuBufferView? get() = null

        override fun validate(vertex: VertexState, vertexBuffers: Map<Int, GpuBufferView>) {
            vertex.validateDrawInputs(vertexBuffers, range.vertexCount, range.firstVertex, range.instanceCount, range.firstInstance)
        }

        override fun bindIndexBuffer(pass: RenderPass) = Unit

        override fun encode(pass: RenderPass) = pass.draw(range)
    }

    data class Indexed(override val range: IndexedRange, val indices: IndexSelection) : DrawInvocation {
        override val indexBuffer: GpuBufferView get() = indices.view

        override fun validate(vertex: VertexState, vertexBuffers: Map<Int, GpuBufferView>) {
            indices.format.validateDrawRange(indices.view, range.indexCount, range.firstIndex)
            vertex.validateIndexedDrawInputs(vertexBuffers, range.indexCount, range.instanceCount, range.firstInstance)
        }

        override fun bindIndexBuffer(pass: RenderPass) = pass.bindIndexBuffer(indices.view, indices.format)

        override fun encode(pass: RenderPass) = pass.drawIndexed(range)
    }
}
