/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

import heckerpowered.render.engine.toUnmodifiableList
import heckerpowered.render.pipeline.vertex.VertexStepMode

/**
 * Describes how one vertex stream stores and advances through its attribute elements.
 *
 * [strideBytes] is the byte distance between elements. Zero repeats the same attribute bytes
 * for every element rather than inferring a packed stride. [stepMode] selects whether elements
 * advance with vertices or instances, and attribute offsets are relative to each element.
 *
 * The attribute list is copied and must be non-empty, with unique meanings and non-negative
 * offsets whose attribute ends fit in an `Int`. Attributes need not fill or fit within one
 * stride; the consumed byte ranges are checked against the source during draw preparation.
 */
class VertexStreamLayout(
    val strideBytes: Int,
    attributes: List<GeometryAttribute>,
    val stepMode: VertexStepMode = VertexStepMode.Vertex,
) {
    val attributes: List<GeometryAttribute> = attributes.toUnmodifiableList()

    init {
        require(strideBytes >= 0)
        require(this.attributes.isNotEmpty())
        require(this.attributes.map { it.meaning }.distinct().size == this.attributes.size)
        require(this.attributes.all { it.offsetBytes >= 0 && it.offsetBytes <= Int.MAX_VALUE - it.format.sizeInBytes })
    }
}
