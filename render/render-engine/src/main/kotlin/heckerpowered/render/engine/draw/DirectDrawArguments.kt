/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

/**
 * Selects the vertex or index elements and instances for one RHI draw.
 *
 * Offsets and counts are measured in elements relative to the bound views, not bytes in a complete
 * allocation. Pipeline, resource bindings, and raster state are selected by the surrounding prepared
 * draw. Zero counts represent a draw with no work, not permission to skip its enclosing pass.
 */
internal sealed interface DirectDrawArguments

/**
 * Selects sequential vertex and instance elements from the bound buffer views.
 *
 * [firstVertex] is the starting vertex element; [firstInstance] is the starting instance element.
 * Each count must be nonnegative. If either count is zero, no primitives are produced.
 */
internal data class NonIndexedDrawArguments(
    val vertexCount: Int,
    val firstVertex: Int = 0,
    val instanceCount: Int = 1,
    val firstInstance: Int = 0,
) : DirectDrawArguments {
    init {
        require(vertexCount >= 0) { "Vertex count must not be negative" }
        require(firstVertex >= 0) { "First vertex must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}

/**
 * Arguments for `RenderPass.drawIndexed`.
 *
 * [baseVertex] remains signed because an indexed range may be relocated backwards relative to the
 * currently bound vertex views. The stored index values are deliberately not inspected here; as in
 * the RHI, the producer is responsible for ensuring that non-restart indices plus [baseVertex]
 * select valid vertex elements.
 */
internal data class IndexedDrawArguments(
    val indexCount: Int,
    val firstIndex: Int = 0,
    val baseVertex: Int = 0,
    val instanceCount: Int = 1,
    val firstInstance: Int = 0,
) : DirectDrawArguments {
    init {
        require(indexCount >= 0) { "Index count must not be negative" }
        require(firstIndex >= 0) { "First index must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}
