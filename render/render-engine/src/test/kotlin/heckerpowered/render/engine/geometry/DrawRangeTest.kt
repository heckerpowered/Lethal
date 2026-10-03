/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry

import heckerpowered.render.engine.geometry.index.IndexSource
import heckerpowered.render.engine.geometry.vertex.GeometryLayout
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.pipeline.primitive.IndexFormat
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertSame

class DrawRangeTest {
    @Test
    fun selectionsExposeElementAndInstanceIntervalsWithoutPreparedDrawData() {
        val vertices = DrawRange.vertices(3, 7, 2, 5)
        assertEquals(VertexRange(3, 7, 2, 5), vertices)
        assertFalse(vertices.isEmpty)

        val indices = DrawRange.indices(6, 3, Int.MIN_VALUE, 4, 2)
        assertEquals(IndexedRange(6, 3, Int.MIN_VALUE, 4, 2), indices)
        assertFalse(indices.isEmpty)
        assertTrue(DrawRange.vertices(0).isEmpty)
        assertTrue(DrawRange.vertices(3, 0, 0).isEmpty)
        assertTrue(DrawRange.indices(0).isEmpty)
        assertTrue(DrawRange.indices(6, 0, 0, 0).isEmpty)
    }

    @Test
    fun negativeCountsAndStartingElementsAreRejectedWithoutReadingStorage() {
        val invalidSelections = listOf<() -> DrawRange>(
            { VertexRange(-1) },
            { VertexRange(1, -1) },
            { VertexRange(1, 0, -1) },
            { VertexRange(1, 0, 1, -1) },
            { IndexedRange(-1) },
            { IndexedRange(1, -1) },
            { IndexedRange(1, 0, 0, -1) },
            { IndexedRange(1, 0, 0, 1, -1) },
        )
        invalidSelections.forEach { selection -> assertFailsWith<IllegalArgumentException> { selection() } }
    }

    @Test
    fun vertexAndShaderGeometryRequireAnIndexSourceExactlyForIndexedSelections() {
        val layout = GeometryLayout(emptyList())
        val primitive = PrimitiveState(PrimitiveTopology.TriangleList)
        val source = IndexSource.Upload(IndexFormat.Uint16, ByteArray(6))
        val vertices = DrawRange.vertices(3)
        val indices = DrawRange.indices(3)

        val vertexSelection = GeometrySelection.Vertices(vertices)
        val indexedSelection = GeometrySelection.Indexed(source, indices)
        val vertexGeometry = VertexGeometry(layout, emptyList(), vertexSelection, primitive)
        val indexedGeometry = VertexGeometry(layout, emptyList(), indexedSelection, primitive)
        val generatedVertices = ShaderGeometry(ParameterValues(), vertexSelection, primitive)
        val generatedIndices = ShaderGeometry(ParameterValues(), indexedSelection, primitive)

        assertSame(vertices, vertexGeometry.range)
        assertSame(indices, indexedGeometry.range)
        assertSame(vertices, generatedVertices.range)
        assertSame(indices, generatedIndices.range)
        assertSame(source, (indexedGeometry.selection as GeometrySelection.Indexed).source)
        assertSame(source, (generatedIndices.selection as GeometrySelection.Indexed).source)

    }
    @Test
    fun selectingRetainsGeometryStorageAndUsesOnlyTheCompleteNewSelection() {
        val original = triangle(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        val source = IndexSource.Upload(IndexFormat.Uint16, ByteArray(6))
        val indices = DrawRange.indices(3, baseVertex = -7)
        val selection = GeometrySelection.Indexed(source, indices)

        val indexed = original.selecting(selection)
        assertSame(original.layout, indexed.layout)
        assertSame(original.streams.single(), indexed.streams.single())
        assertSame(original.primitive, indexed.primitive)
        assertSame(selection, indexed.selection)
        assertSame(indices, indexed.range)
        assertSame(source, (indexed.selection as GeometrySelection.Indexed).source)

        val vertices = GeometrySelection.Vertices(DrawRange.vertices(3, instances = 0))
        val nonIndexed = indexed.selecting(vertices)
        assertSame(vertices, nonIndexed.selection)
        assertSame(vertices.range, nonIndexed.range)
        assertTrue(nonIndexed.range.isEmpty)
        assertSame(original.streams.single(), nonIndexed.streams.single())
    }

}
