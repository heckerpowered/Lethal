/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene.drawing

import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.VertexRange
import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.pipeline.vertex.VertexStepMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class RectangleGeometryTest {
    @Test
    fun rectangleCornersAndUvHaveTheOriginalNonIndexedStripLayout() {
        val geometry = rectangleGeometry(Rectangle(2f, 3f, 5f, 7f), Rectangle(-1f, 2f, 3f, 4f))
        val layout = geometry.layout.streams.single()
        assertEquals(20, layout.strideBytes)
        assertEquals(VertexStepMode.Vertex, layout.stepMode)
        assertEquals(listOf(VertexSemantics.Position, VertexSemantics.UV), layout.attributes.map { it.semantic })
        assertEquals(listOf(VertexFormat.Float32x3, VertexFormat.Float32x2), layout.attributes.map { it.format })
        assertEquals(listOf(0, 12), layout.attributes.map { it.offsetBytes })
        assertEquals(VertexRange(4), geometry.range)
        assertEquals(PrimitiveTopology.TriangleStrip, geometry.primitive.topology)
        assertIs<GeometrySelection.Vertices>(geometry.selection)
        val bytes = ByteBuffer.allocate(80).order(ByteOrder.nativeOrder())
        (geometry.streams.single() as VertexStreamSource.Upload).bytes.copyTo(bytes)
        val expected = floatArrayOf(
            2f, 3f, 0f, -1f, 2f,
            2f, 10f, 0f, -1f, 6f,
            7f, 3f, 0f, 2f, 2f,
            7f, 10f, 0f, 2f, 6f,
        )
        assertContentEquals(expected, FloatArray(20) { bytes.getFloat(it * Float.SIZE_BYTES) })
    }

    @Test
    fun packedBytesMatchTheOriginalCornerArithmeticIncludingSignedZeroAndOverflow() {
        val rectangles = listOf(
            Rectangle(2f, 3f, 5f, 7f),
            Rectangle(-0f, -0f, 0f, -0f),
            Rectangle(-1f, 2f, 0f, 0f),
            Rectangle(Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
        )
        for (rectangle in rectangles) for (uv in rectangles) {
            val expected = ByteBuffer.allocate(80).order(ByteOrder.nativeOrder())
            for ([x, y] in listOf(0f to 0f, 0f to 1f, 1f to 0f, 1f to 1f)) {
                expected.putFloat(rectangle.x + x * rectangle.width).putFloat(rectangle.y + y * rectangle.height)
                    .putFloat(0f).putFloat(uv.x + x * uv.width).putFloat(uv.y + y * uv.height)
            }
            val actual = ByteBuffer.allocate(80)
            (rectangleGeometry(rectangle, uv).streams.single() as VertexStreamSource.Upload).bytes.copyTo(actual)
            assertContentEquals(expected.array(), actual.array())
        }
    }
}
