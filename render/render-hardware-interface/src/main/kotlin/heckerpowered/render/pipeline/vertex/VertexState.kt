/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.vertex

import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.buffer.GpuBufferView

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

    /**
     * Checks the vertex-input selections and byte ranges of a non-indexed draw.
     *
     * Every layout containing attributes requires a binding at its list position. Vertex-rate
     * bindings select `[firstVertex, firstVertex + vertexCount)`; instance-rate bindings select
     * `[firstInstance, firstInstance + instanceCount)`. Slots with no attributes need no buffer,
     * and extra bindings are not consumed by this vertex state.
     *
     * If either count is zero, bindings and argument validity are still checked, but no element
     * range is read. An empty [buffers] list supports buffer-free procedural geometry.
     *
     * This checks only vertex input, not the pipeline, attachments, descriptors, or push constants.
     *
     * @throws IllegalArgumentException if arguments, usages, or fetched byte ranges are invalid.
     * @throws IllegalStateException if a required vertex-buffer slot is unbound.
     */
    fun validateDrawInputs(boundBuffers: Map<Int, GpuBufferView>, vertexCount: Int, firstVertex: Int = 0, instanceCount: Int = 1, firstInstance: Int = 0) {
        require(vertexCount >= 0) { "Vertex count must not be negative" }
        require(firstVertex >= 0) { "First vertex must not be negative" }
        validateInstanceArguments(instanceCount, firstInstance)
        val hasWork = vertexCount > 0 && instanceCount > 0

        buffers.forEachIndexed { slot, layout ->
            if (layout.attributes.isEmpty()) return@forEachIndexed
            val view = checkNotNull(boundBuffers[slot]) { "Vertex-buffer slot $slot is unbound" }
            when (layout.stepMode) {
                VertexStepMode.Vertex -> layout.validateAccess(view, firstVertex.toLong(), if (hasWork) vertexCount else 0, "Vertex-buffer slot $slot")
                VertexStepMode.Instance -> layout.validateAccess(view, firstInstance.toLong(), if (hasWork) instanceCount else 0, "Instance-buffer slot $slot")
            }
        }
    }

    /**
     * Checks required vertex bindings and the instance-data range of an indexed draw.
     *
     * Index-buffer bounds are checked separately by `IndexFormat.validateDrawRange`. Index values
     * determine which vertex-rate elements are fetched, so this method cannot prove those byte
     * ranges from metadata. It deliberately does not treat `indexCount` as a vertex bound, reject
     * a negative base-vertex offset by itself, or read back the index buffer to guess its contents.
     * The actual non-restart indices plus the base offset must select valid vertex elements.
     *
     * Instance-rate access does not depend on index values, and is checked for the requested
     * instances when both counts are positive. A stream containing only restart markers may
     * consume fewer attributes; that does not relax the declared instance-data range requirement.
     *
     * @throws IllegalArgumentException if arguments, usages, or the instance-data range are invalid.
     * @throws IllegalStateException if a required vertex-buffer slot is unbound.
     */
    fun validateIndexedDrawInputs(boundBuffers: Map<Int, GpuBufferView>, indexCount: Int, instanceCount: Int = 1, firstInstance: Int = 0) {
        require(indexCount >= 0) { "Index count must not be negative" }
        validateInstanceArguments(instanceCount, firstInstance)
        val hasWork = indexCount > 0 && instanceCount > 0

        buffers.forEachIndexed { slot, layout ->
            if (layout.attributes.isEmpty()) return@forEachIndexed
            val view = checkNotNull(boundBuffers[slot]) { "Vertex-buffer slot $slot is unbound" }
            when (layout.stepMode) {
                VertexStepMode.Vertex -> layout.validateAccess(view, 0L, 0, "Vertex-buffer slot $slot")
                VertexStepMode.Instance -> layout.validateAccess(view, firstInstance.toLong(), if (hasWork) instanceCount else 0, "Instance-buffer slot $slot")
            }
        }
    }

    private fun validateInstanceArguments(instanceCount: Int, firstInstance: Int) {
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }

    companion object {
        val Empty = VertexState(emptyList())
    }
}