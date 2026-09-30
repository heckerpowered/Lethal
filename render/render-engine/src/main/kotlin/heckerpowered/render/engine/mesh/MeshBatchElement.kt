/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

/**
 * Selects one draw range from the vertex factory shared by a [MeshBatch].
 *
 * Elements describe geometry-relative draw arguments only. They do not select shaders, pipelines,
 * descriptor sets, render targets, or other pass-specific execution state.
 */
sealed interface MeshBatchElement {
    val instanceCount: Int
    val firstInstance: Int
}

/**
 * Selects a consecutive non-indexed vertex range from the batch's [VertexFactory].
 *
 * The factory determines how the selected vertex and instance indices obtain their attributes. A
 * later mesh-pass processor lowers these values to the corresponding RHI draw.
 */
data class VertexMeshBatchElement(
    val vertexCount: Int,
    val firstVertex: Int = 0,
    override val instanceCount: Int = 1,
    override val firstInstance: Int = 0,
) : MeshBatchElement {
    init {
        require(vertexCount >= 0) { "Vertex count must not be negative" }
        require(firstVertex >= 0) { "First vertex must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }
    }
}

/**
 * Selects a consecutive indexed range using [indices] and the batch's [VertexFactory].
 *
 * [baseVertex] is signed because relocating a local index sequence may require a negative offset.
 * The stored indices are deliberately not inspected on the CPU; the producer remains responsible
 * for ensuring that their eventual vertex accesses are valid after [baseVertex] is applied.
 */
data class IndexedMeshBatchElement(
    val indices: MeshIndexBuffer,
    val indexCount: Int,
    val firstIndex: Int = 0,
    val baseVertex: Int = 0,
    override val instanceCount: Int = 1,
    override val firstInstance: Int = 0,
) : MeshBatchElement {
    init {
        require(indexCount >= 0) { "Index count must not be negative" }
        require(firstIndex >= 0) { "First index must not be negative" }
        require(instanceCount >= 0) { "Instance count must not be negative" }
        require(firstInstance >= 0) { "First instance must not be negative" }

        indices.format.validateDrawRange(indices.view, indexCount, firstIndex)
    }
}
