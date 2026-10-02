/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

import heckerpowered.render.pipeline.vertex.VertexFormat

/**
 * Describes a vertex attribute's semantic, stored format, and byte offset within an element.
 *
 * [offsetBytes] is measured from the beginning of an element, not from the complete buffer or
 * bound view. The containing [VertexStreamLayout] supplies the stride and stepping behavior;
 * the shader input contract maps [semantic] to a shader input location.
 */
data class GeometryAttribute(
    val semantic: VertexSemantic,
    val format: VertexFormat,
    val offsetBytes: Int,
)
