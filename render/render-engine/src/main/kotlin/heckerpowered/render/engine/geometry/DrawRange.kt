/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.command.pass.RenderPass

/**
 * Selects the vertex or index elements and instances drawn from a geometry's storage.
 *
 * Counts and starting positions are measured in elements, not bytes. Selection starts at the
 * beginning of each bound view; the view supplies its offset within the complete allocation.
 * The same storage can therefore be reused for several submeshes by choosing different ranges.
 *
 * Counts and first positions must be non-negative. A zero element or instance count produces no
 * rendering work. Constructing a range validates its arguments, not the capacity or contents of
 * the storage from which those elements will be fetched.
 */
sealed interface DrawRange {
    val instanceCount: Int
    val firstInstance: Int

    /** Whether element or instance count is zero; an enclosing pass still applies its attachment operations. */
    val isEmpty: Boolean
}

/**
 * Selects consecutive vertices starting at [firstVertex]. Instance-rate streams independently
 * select [instanceCount] elements starting at [firstInstance].
 */
data class VertexRange(
    val vertexCount: Int,
    val firstVertex: Int = 0,
    override val instanceCount: Int = 1,
    override val firstInstance: Int = 0,
) : DrawRange {
    override val isEmpty: Boolean
        get() = vertexCount == 0 || instanceCount == 0

    init {
        require(vertexCount >= 0) { "Vertex count must not be negative" }
        require(firstVertex >= 0) { "First vertex must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}

fun RenderPass.draw(range: VertexRange) {
    draw(range.vertexCount, range.firstVertex, range.instanceCount, range.firstInstance)
}

/**
 * Selects consecutive index entries and an independent interval of instance elements.
 * [indexCount] includes restart markers. Non-restart index values plus the signed [baseVertex]
 * select vertex elements. A negative base offset is valid when the resulting selections are in bounds.
 */
data class IndexedRange(
    val indexCount: Int,
    val firstIndex: Int = 0,
    val baseVertex: Int = 0,
    override val instanceCount: Int = 1,
    override val firstInstance: Int = 0,
) : DrawRange {
    override val isEmpty: Boolean
        get() = indexCount == 0 || instanceCount == 0

    init {
        require(indexCount >= 0) { "Index count must not be negative" }
        require(firstIndex >= 0) { "First index must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}

fun RenderPass.drawIndexed(range: IndexedRange) {
    drawIndexed(range.indexCount, range.firstIndex, range.baseVertex, range.instanceCount, range.firstInstance)
}
