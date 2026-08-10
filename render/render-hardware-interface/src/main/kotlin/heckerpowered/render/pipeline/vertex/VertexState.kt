/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

import heckerpowered.render.GpuBuffer

/**
 * Describes how vertex buffers provide input attributes to the vertex shader.
 *
 * A vertex state consists of zero or more vertex-buffer bindings. Each binding describes one
 * buffer stream, including its stride, stepping behavior, and the shader input attributes sourced
 * from that stream.
 *
 * The position of a [VertexBufferLayout] in [buffers] determines its binding index. The buffer
 * bound at slot `n` is interpreted according to `buffers[n]`.
 *
 * Attribute locations are global to the complete vertex state rather than local to an individual
 * buffer binding. Each location therefore identifies at most one [VertexAttribute] across all
 * elements of [buffers].
 *
 * For example:
 *
 * ```
 * binding 0 — per vertex, stride 32
 *   location 0 -> Float32x3 at offset 0   // position
 *   location 1 -> Float32x3 at offset 12  // normal
 *   location 2 -> Float32x2 at offset 24  // texture coordinates
 *
 * binding 1 — per instance, stride 64
 *   location 3 -> Float32x4 at offset 0
 *   location 4 -> Float32x4 at offset 16
 *   location 5 -> Float32x4 at offset 32
 *   location 6 -> Float32x4 at offset 48  // instance transform
 * ```
 *
 * Vertex state describes only how bound buffers are interpreted. It does not reference or own the
 * actual [GpuBuffer]s used by a draw; those buffers are supplied separately while encoding
 * rendering commands.
 *
 * An empty vertex state is valid for shaders that generate vertex data without vertex-buffer
 * inputs, such as fullscreen triangles derived from the vertex index.
 */
data class VertexState(
    val buffers: List<VertexBufferLayout>,
) {
    init {
        buffers.flatMap(VertexBufferLayout::attributes)
            .distinctBy(VertexAttribute::location)
            .let { uniqueAttributes ->
                require(uniqueAttributes.size == buffers.flatMap(VertexBufferLayout::attributes).size) { "Vertex attribute locations must be unique across every buffer binding" }
            }
    }

    companion object {
        val Empty = VertexState(emptyList())
    }
}