/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.geometry.vertex.GeometryAttribute
import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.pipeline.vertex.VertexStepMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class TriangleTest {
    @Test
    fun mixedPositionsKeepTheirOrderPaddingAndNativeByteLayout() {
        val a = floatArrayOf(-0f, 2f)
        val b = floatArrayOf(3f, 4f, -0f)
        val c = floatArrayOf(5f, 6f, 7f)
        val geometry = triangle(a, b, c)
        a.fill(99f)
        b.fill(99f)
        c.fill(99f)
        val layout = geometry.layout.streams.single()
        assertEquals(12, layout.strideBytes)
        assertEquals(VertexStepMode.Vertex, layout.stepMode)
        assertEquals(listOf(GeometryAttribute(VertexSemantics.Position, VertexFormat.Float32x3, 0)), layout.attributes)
        assertEquals(VertexRange(3), geometry.range)
        assertEquals(PrimitiveTopology.TriangleList, geometry.primitive.topology)
        assertIs<GeometrySelection.Vertices>(geometry.selection)
        val upload = (geometry.streams.single() as VertexStreamSource.Upload).bytes
        assertEquals(36, upload.sizeBytes)
        val actual = ByteBuffer.allocate(36)
        upload.copyTo(actual)
        val expected = ByteBuffer.allocate(36).order(ByteOrder.nativeOrder())
        floatArrayOf(-0f, 2f, 0f, 3f, 4f, -0f, 5f, 6f, 7f).forEach(expected::putFloat)
        assertContentEquals(expected.array(), actual.array())
    }

    @Test
    fun invalidCoordinateCountsAreRejectedForEveryVertex() {
        val valid = floatArrayOf(0f, 1f)
        for (size in listOf(0, 1, 4)) {
            assertFailsWith<IllegalArgumentException> { triangle(FloatArray(size), valid, valid) }
            assertFailsWith<IllegalArgumentException> { triangle(valid, FloatArray(size), valid) }
            assertFailsWith<IllegalArgumentException> { triangle(valid, valid, FloatArray(size)) }
        }
    }
}
