/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.collection.toUnmodifiableList
import heckerpowered.render.engine.geometry.index.IndexSource
import heckerpowered.render.engine.geometry.vertex.GeometryLayout
import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.geometry.vertex.geometryLayout
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
 * shader locations. The index source must be present exactly when [range] is indexed.
 *
 * The source list is copied. Upload sources contain host-byte snapshots, while resident sources
 * keep references to existing GPU views whose contents may still change. Resident resources must
 * remain valid through GPU completion. Construction checks the stream count and indexed form;
 * pass preparation and RHI validation check consumed attributes and accessible byte ranges.
 */
class VertexGeometry(
    val layout: GeometryLayout,
    streams: List<VertexStreamSource>,
    val indices: IndexSource?,
    override val range: DrawRange,
    override val primitive: PrimitiveState,
    override val protocol: GeometryProtocol = GeometryProtocol("attributes"),
) : RenderGeometry {
    val streams = streams.toUnmodifiableList()

    init {
        require(this.streams.size == layout.streams.size)
        val hasMatchingIndexSource = (range is IndexedRange) == (indices != null)
        require(hasMatchingIndexSource) { "Indexed ranges require an index source; vertex ranges require none" }
    }

    /** Reuses the same layout and sources with a different element and instance selection. */
    fun selecting(range: DrawRange): VertexGeometry =
        VertexGeometry(layout, streams, indices, range, primitive, protocol)
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

    return VertexGeometry(layout, listOf(VertexStreamSource.Upload(data.array())), null, DrawRange.vertices(3), PrimitiveState(PrimitiveTopology.TriangleList))
}