/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

/**
 * Draw arguments that can be translated directly to one RHI draw command.
 *
 * This type contains no geometry storage, pipeline, shader resources, viewport, scissor, or
 * attachment state. Those inputs are selected separately by [PreparedDirectDraw] or by the pass
 * coordinator that executes it.
 */
internal sealed interface DirectDrawArguments

/**
 * Arguments for `RenderPass.draw`.
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
