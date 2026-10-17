/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.engine.support.collection.toUnmodifiableMap
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.resource.buffer.GpuBufferView

/** Snapshots vertex bindings for an invocation without copying storage or extending resource lifetime. */
internal class PreparedGeometry(vertexBuffers: Map<Int, GpuBufferView>, val invocation: DrawInvocation) {
    val vertexBuffers = vertexBuffers.toUnmodifiableMap()

    fun validate(vertex: VertexState) {
        invocation.validate(vertex, vertexBuffers)
    }
}

internal fun RenderPass.bindGeometry(geometry: PreparedGeometry) {
    geometry.vertexBuffers.forEach { [slot, view] -> bindVertexBuffer(slot, view) }
    val invocation = geometry.invocation
    if (invocation is DrawInvocation.Indexed) {
        bindIndexBuffer(invocation.indices.view, invocation.indices.format)
    }
}
