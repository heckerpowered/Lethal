/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.pipeline.vertex.VertexLayoutDsl
import heckerpowered.render.pipeline.vertex.VertexStepMode

fun geometryLayout(block: GeometryLayoutBuilder.() -> Unit): GeometryLayout =
    GeometryLayoutBuilder().apply(block).build()

fun geometryLayout(strideBytes: Int, stepMode: VertexStepMode = VertexStepMode.Vertex, block: VertexStreamLayoutBuilder.() -> Unit): GeometryLayout =
    geometryLayout { stream(strideBytes, stepMode, block) }

@VertexLayoutDsl
class GeometryLayoutBuilder internal constructor() {
    private val streams = mutableListOf<VertexStreamLayout>()

    fun stream(strideBytes: Int, stepMode: VertexStepMode = VertexStepMode.Vertex, block: VertexStreamLayoutBuilder.() -> Unit) {
        streams += VertexStreamLayoutBuilder(strideBytes, stepMode).apply(block).build()
    }

    internal fun build(): GeometryLayout = GeometryLayout(streams)
}

@VertexLayoutDsl
class VertexStreamLayoutBuilder internal constructor(
    private val strideBytes: Int,
    private val stepMode: VertexStepMode,
) {
    private val attributes = mutableListOf<GeometryAttribute>()

    fun attribute(semantic: VertexSemantic, format: VertexFormat, offsetBytes: Int) {
        attributes += GeometryAttribute(semantic, format, offsetBytes)
    }

    internal fun build(): VertexStreamLayout = VertexStreamLayout(strideBytes, attributes, stepMode)
}
