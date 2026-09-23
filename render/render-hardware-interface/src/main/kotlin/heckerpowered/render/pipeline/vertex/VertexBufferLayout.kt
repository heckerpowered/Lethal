/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

import heckerpowered.render.buffer.BufferUsage
import heckerpowered.render.buffer.GpuBufferView

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
    /**
     * Byte distance between consecutive elements. Zero keeps every element at the same address;
     * it does not request an automatically inferred packed stride.
     */
    val stride: Int,
    val stepMode: VertexStepMode = VertexStepMode.Vertex,
    val attributes: List<VertexAttribute>,
) {
    init {
        require(stride >= 0) { "Vertex buffer stride must not be negative" }
    }

    /**
     * Checks the bytes fetched from [view] for a consecutive interval of vertex or instance elements.
     *
     * [firstElement] is relative to the bound view, not the complete buffer. Each attribute reads
     * at `element * stride + attribute.offset` within that view. The final element needs only
     * its actual attribute bytes, not padding up to the next stride. For example, three elements
     * with stride 32 and one 12-byte attribute at offset zero require 76 bytes, not 96.
     *
     * A zero count performs no attribute reads, but usage and argument checks still apply. With
     * zero stride, any non-empty element interval reads the same attribute bytes. This method
     * checks every declared attribute; it does not inspect shader code or CPU/GPU buffer contents.
     *
     * Device alignment, format support, resource validity, and synchronization remain use-site checks.
     *
     * @throws IllegalArgumentException if a parameter or binding is invalid, or fetched bytes
     * would extend outside [view].
     */
    fun validateAccess(view: GpuBufferView, firstElement: Long, elementCount: Int, context: String = "Vertex buffer") {
        require(firstElement >= 0) { "$context first element must not be negative" }
        require(elementCount >= 0) { "$context element count must not be negative" }
        require(BufferUsage.Vertex in view.buffer.usage) { "$context requires BufferUsage.Vertex" }
        require(view.sizeBytes > 0) { "$context requires a non-empty buffer range" }
        if (elementCount == 0 || attributes.isEmpty()) return

        val attributeEnd = attributes.maxOf { it.offset.toLong() + it.format.sizeInBytes }
        require(attributeEnd <= view.sizeBytes) { "$context cannot contain its attribute bytes" }
        if (stride == 0) return

        // Compare element capacity instead of multiplying a potentially invalid large index.
        val lastAvailableElement = (view.sizeBytes - attributeEnd) / stride
        require(firstElement <= lastAvailableElement) { "$context first element exceeds its bound range" }
        require(elementCount.toLong() - 1 <= lastAvailableElement - firstElement) { "$context attribute fetch exceeds its bound range" }
    }
}