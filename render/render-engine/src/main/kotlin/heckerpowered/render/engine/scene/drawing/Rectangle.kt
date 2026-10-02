/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene.drawing

import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.VertexGeometry
import heckerpowered.render.engine.geometry.vertex.*
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.VertexFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A rectangle extending from [x], [y] in the positive coordinate directions.
 *
 * Drawing rectangles use the caller's geometry units, while UV rectangles use texture coordinates.
 * Empty extents are representable. Coordinates must be finite and extents nonnegative; this type
 * does not restrict UV coordinates to `[0, 1]` or select a framebuffer coordinate mapping.
 */
data class Rectangle(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(listOf(x, y, width, height).all { it.isFinite() })
        require(width >= 0 && height >= 0)
    }
}

/** Creates a local-space quad at Z = 0, mapping corresponding corners of [rectangle] and [uv]. */
fun rectangleGeometry(rectangle: Rectangle, uv: Rectangle = Rectangle(0f, 0f, 1f, 1f)): VertexGeometry {
    // Preserve the original corner arithmetic, including its signed-zero results.
    val startX = rectangle.x + 0f * rectangle.width
    val startY = rectangle.y + 0f * rectangle.height
    val startU = uv.x + 0f * uv.width
    val startV = uv.y + 0f * uv.height

    val endX = rectangle.x + rectangle.width
    val endY = rectangle.y + rectangle.height
    val endU = uv.x + uv.width
    val endV = uv.y + uv.height

    val coordinates = floatArrayOf(
        startX, startY, 0f, startU, startV,
        startX, endY, 0f, startU, endV,
        endX, startY, 0f, endU, startV,
        endX, endY, 0f, endU, endV,
    )

    val vertices = ByteBuffer
        .allocate(coordinates.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    vertices.asFloatBuffer().put(coordinates)

    val attributes = listOf(
        GeometryAttribute(VertexSemantics.Position, VertexFormat.Float32x3, 0),
        GeometryAttribute(VertexSemantics.UV, VertexFormat.Float32x2, 12),
    )
    val layout = GeometryLayout(listOf(VertexStreamLayout(20, attributes)))
    val streams = listOf(VertexStreamSource.Upload(vertices.array()))
    return VertexGeometry(layout, streams, null, DrawRange.vertices(4), PrimitiveState(PrimitiveTopology.TriangleStrip))
}
