/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.geometry.vertex.GeometryLayout
import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.geometry.vertex.geometryLayout
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.VertexFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Describes geometry stored as named attributes in vertex streams.
 *
 * Each source in [streams] supplies the stream at the same position in [layout]. Attribute
 * semantics describe what the values mean; the shader input contract assigns their
 * shader locations. [selection] couples an indexed range with its index source, or selects
 * vertices directly without an index source.
 *
 * The source list is copied. Upload sources contain host-byte snapshots, while resident sources
 * keep references to existing GPU views whose contents may still change. Resident resources must
 * remain valid through GPU completion. Construction checks the stream count;
 * pass preparation and RHI validation check consumed attributes and accessible byte ranges.
 */
class VertexGeometry(
    val layout: GeometryLayout,
    streams: List<VertexStreamSource>,
    override val selection: GeometrySelection,
    override val primitive: PrimitiveState,
) : RenderGeometry {
    val streams = streams.toUnmodifiableList()

    init {
        require(this.streams.size == layout.streams.size)
    }

    /** Reuses the same layout and sources with a different element and instance selection. */
    fun selecting(selection: GeometrySelection): VertexGeometry =
        VertexGeometry(layout, streams, selection, primitive)
}

/**
 * Snapshots three local-space positions into one non-indexed triangle with a position attribute.
 * Each array must contain two or three coordinates; two-dimensional positions use `z = 0`.
 */
fun triangle(a: FloatArray, b: FloatArray, c: FloatArray): VertexGeometry {
    val positions = listOf(a, b, c)
    require(positions.all { it.size == 2 || it.size == 3 })

    val data = ByteBuffer.allocate(36).order(ByteOrder.nativeOrder())
    for (position in positions) {
        data.putFloat(position[0])
        data.putFloat(position[1])
        data.putFloat(if (position.size == 3) position[2] else 0f)
    }

    val layout = geometryLayout(strideBytes = 12) {
        attribute(VertexSemantics.Position, VertexFormat.Float32x3, offsetBytes = 0)
    }

    return VertexGeometry(layout, listOf(VertexStreamSource.Upload(data.array())), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))
}