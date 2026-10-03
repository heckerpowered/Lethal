/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.geometry.vertex

import heckerpowered.render.pipeline.vertex.VertexFormat
import heckerpowered.render.pipeline.vertex.VertexStepMode
import kotlin.test.*

class GeometryLayoutBuilderTest {
    @Test
    fun streamAndAttributeOrderMatchesTheExistingDescriptions() {
        val layout = geometryLayout {
            stream(strideBytes = 20) {
                attribute(VertexSemantics.Position, VertexFormat.Float32x3, offsetBytes = 0)
                attribute(VertexSemantics.UV, VertexFormat.Float32x2, offsetBytes = 12)
            }
            stream(strideBytes = 16, stepMode = VertexStepMode.Instance) {
                attribute(VertexSemantics.Color, VertexFormat.Float32x4, offsetBytes = 0)
            }
        }
        assertEquals(listOf(20, 16), layout.streams.map { it.strideBytes })
        assertEquals(listOf(VertexStepMode.Vertex, VertexStepMode.Instance), layout.streams.map { it.stepMode })
        assertEquals(listOf(
            GeometryAttribute(VertexSemantics.Position, VertexFormat.Float32x3, 0),
            GeometryAttribute(VertexSemantics.UV, VertexFormat.Float32x2, 12),
        ), layout.streams.first().attributes)
        assertEquals(listOf(GeometryAttribute(VertexSemantics.Color, VertexFormat.Float32x4, 0)), layout.streams.last().attributes)
    }

    @Test
    fun buildersCannotChangeAnAlreadyBuiltDescription() {
        val stream = VertexStreamLayoutBuilder(20, VertexStepMode.Vertex)
        stream.attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0)
        val first = stream.build()
        stream.attribute(VertexSemantics.UV, VertexFormat.Float32x2, 12)
        assertEquals(1, first.attributes.size)
        assertEquals(2, stream.build().attributes.size)
        assertFailsWith<UnsupportedOperationException> { (first.attributes as MutableList).clear() }

        val builder = GeometryLayoutBuilder()
        builder.stream(12) { attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0) }
        val layout = builder.build()
        builder.stream(8) { attribute(VertexSemantics.UV, VertexFormat.Float32x2, 0) }
        assertEquals(1, layout.streams.size)
        assertEquals(2, builder.build().streams.size)
        assertFailsWith<UnsupportedOperationException> { (layout.streams as MutableList).clear() }
    }

    @Test
    fun existingValidationAppliesToDslDescriptions() {
        assertTrue(geometryLayout {}.streams.isEmpty())
        assertFailsWith<IllegalArgumentException> { geometryLayout(12) {} }
        assertFailsWith<IllegalArgumentException> {
            geometryLayout(-1) { attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0) }
        }
        assertFailsWith<IllegalArgumentException> {
            geometryLayout(12) { attribute(VertexSemantics.Position, VertexFormat.Float32x3, -1) }
        }
        assertFailsWith<IllegalArgumentException> {
            geometryLayout(12) { attribute(VertexSemantics.Position, VertexFormat.Float32x3, Int.MAX_VALUE) }
        }
        assertFailsWith<IllegalArgumentException> {
            geometryLayout(24) {
                attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0)
                attribute(VertexSemantics.Position, VertexFormat.Float32x3, 12)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            geometryLayout {
                stream(12) { attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0) }
                stream(12) { attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0) }
            }
        }
    }
}
