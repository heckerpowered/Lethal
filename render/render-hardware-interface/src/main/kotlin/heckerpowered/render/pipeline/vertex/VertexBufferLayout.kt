/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

/**
 * Describes one vertex-buffer binding within a [VertexState].
 *
 * [stride] is the byte distance between consecutive elements in the bound buffer. [stepMode]
 * determines whether advancing to the next element follows the vertex index or instance index.
 *
 * [attributes] selects the shader input locations sourced from this binding. Attribute offsets
 * are measured in bytes from the beginning of each element.
 *
 * The binding index is not stored by this object. It is determined by this layout's position in
 * [VertexState.buffers].
 */
data class VertexBufferLayout(
    val stride: Int,
    val stepMode: VertexStepMode = VertexStepMode.Vertex,
    val attributes: List<VertexAttribute>,
) {
    init {
        require(stride >= 0) { "Vertex buffer stride must not be negative" }
    }
}