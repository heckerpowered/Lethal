/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * A renderable index sequence used by indexed [MeshBatchElement]s.
 *
 * Index storage belongs to the individual batch element rather than to [VertexInput]: several
 * elements may share one vertex input while selecting different index buffers or index formats.
 * Vertex feeding and indexed draw selection are therefore kept as independent pieces of a mesh
 * contribution.
 *
 * The buffer range is validated as a complete sequence of [format] values on construction. The
 * element that uses it separately validates its selected `firstIndex/indexCount` range.
 */
data class MeshIndexBuffer(
    val view: GpuBufferView,
    val format: IndexFormat,
) {
    init {
        format.validateBinding(view)
    }
}