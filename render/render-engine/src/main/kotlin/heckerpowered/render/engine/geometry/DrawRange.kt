/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

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

    companion object {
        /**
         * Selects consecutive vertex elements starting at [first]. Instance-rate streams instead
         * select [instances] elements starting at [firstInstance].
         */
        fun vertices(count: Int, first: Int = 0, instances: Int = 1, firstInstance: Int = 0) =
            VertexRange(count, first, instances, firstInstance)

        /**
         * Selects [count] index entries starting at [first], including any restart markers.
         * Non-restart index values plus the signed [baseVertex] select vertex-rate elements;
         * instance-rate streams use [firstInstance] independently of those indices.
         * A negative base offset is valid when the resulting vertex selections remain in bounds.
         */
        fun indices(count: Int, first: Int = 0, baseVertex: Int = 0, instances: Int = 1, firstInstance: Int = 0) =
            IndexedRange(count, first, baseVertex, instances, firstInstance)
    }
}

/** Selects consecutive vertex elements and an independent interval of instance elements. */
data class VertexRange(
    val vertexCount: Int,
    val firstVertex: Int = 0,
    override val instanceCount: Int = 1,
    override val firstInstance: Int = 0,
) : DrawRange {
    init {
        require(vertexCount >= 0) { "Vertex count must not be negative" }
        require(firstVertex >= 0) { "First vertex must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}

/**
 * Selects consecutive index entries and an independent interval of instance elements.
 * Non-restart index values plus the signed [baseVertex] select vertex elements.
 */
data class IndexedRange(
    val indexCount: Int,
    val firstIndex: Int = 0,
    val baseVertex: Int = 0,
    override val instanceCount: Int = 1,
    override val firstInstance: Int = 0,
) : DrawRange {
    init {
        require(indexCount >= 0) { "Index count must not be negative" }
        require(firstIndex >= 0) { "First index must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}
