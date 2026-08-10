/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

import heckerpowered.render.GpuBuffer

/**
 * Describes one value fetched from a vertex-buffer element and supplied to the vertex shader.
 *
 * The containing [VertexBufferLayout] determines which vertex-buffer binding supplies the data.
 * An attribute then selects a value within each element of that binding and maps the resulting
 * value to a vertex-shader input.
 */
data class VertexAttribute(
    /**
     * Vertex-shader input location that receives this attribute.
     *
     * Locations belong to the complete [VertexState], not to an individual
     * [VertexBufferLayout]. Each location may therefore occur at most once across all buffer
     * bindings in the same vertex state.
     *
     * The location does not identify the source vertex-buffer binding. That binding is determined
     * by the [VertexBufferLayout] containing this attribute.
     */
    val location: Int,

    /**
     * Format used to interpret the attribute data in the vertex buffer.
     *
     * The format determines the number of components, their stored numeric representation, and
     * any conversion or normalization performed before the value is supplied to the vertex
     * shader.
     *
     * For example, `Float32x3` reads three consecutive 32-bit floating-point components, while a
     * normalized integer format converts its stored integer components to normalized values.
     */
    val format: VertexFormat,

    /**
     * Byte offset of the attribute from the beginning of each element in the containing
     * [VertexBufferLayout].
     *
     * For example, with a stride of 32 bytes, a `Float32x2` attribute at offset 24 reads the final
     * eight bytes of each element.
     *
     * This offset is relative to the current vertex-buffer element, not to the beginning of the
     * bound [GpuBuffer].
     */
    val offset: Int,
) {
    init {
        require(location >= 0) { "Vertex attribute location must not be negative" }
        require(offset >= 0) { "Vertex attribute offset must not be negative" }
    }
}