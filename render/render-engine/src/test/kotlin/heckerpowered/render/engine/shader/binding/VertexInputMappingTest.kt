/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.binding

import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.geometry.VertexGeometry
import heckerpowered.render.engine.geometry.vertex.GeometryLayout
import heckerpowered.render.engine.geometry.vertex.VertexSemantics
import heckerpowered.render.engine.geometry.vertex.VertexStreamSource
import heckerpowered.render.engine.geometry.vertex.geometryLayout
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.pipeline.vertex.*
import kotlin.test.*

class VertexInputMappingTest {
    @Test
    fun loweringPreservesStreamSlotsAndStorageWhileSelectingAttributesBySemantic() {
        val geometry = geometry(geometryLayout {
            stream(16, VertexStepMode.Instance) { attribute(VertexSemantics.Color, VertexFormat.Float32x4, 0) }
            stream(28) {
                attribute(VertexSemantics.Position, VertexFormat.Float32x3, 4)
                attribute(VertexSemantics.UV, VertexFormat.Float32x2, 20)
            }
        })
        val inputs = VertexInputMapping(
            listOf(
                AttributeInput(VertexSemantics.UV, 7, VertexFormat.Float32x2),
                AttributeInput(VertexSemantics.Position, 3, VertexFormat.Float32x3),
            )
        )

        val state = inputs.lower(geometry)

        assertEquals(
            listOf(
                VertexBufferLayout(16, VertexStepMode.Instance, emptyList()),
                VertexBufferLayout(
                    28, VertexStepMode.Vertex, listOf(
                        VertexAttribute(3, VertexFormat.Float32x3, 4),
                        VertexAttribute(7, VertexFormat.Float32x2, 20),
                    )
                ),
            ), state.buffers
        )
        val unusedInputs = VertexInputMapping(emptyList()).lower(geometry)
        assertEquals(listOf(16, 28), unusedInputs.buffers.map { it.stride })
        assertTrue(unusedInputs.buffers.all { it.attributes.isEmpty() })
    }

    @Test
    fun inputValidationKeepsDeclarationOrderAndPrecedesStreamLowering() {
        val geometry = geometry(geometryLayout(12) {
            attribute(VertexSemantics.Position, VertexFormat.Float32x3, 0)
        })
        val position = AttributeInput(VertexSemantics.Position, -1, VertexFormat.Float32x2)
        val missing = AttributeInput(VertexSemantics.Normal, 1, VertexFormat.Float32x3)

        val formatFailure = assertFailsWith<IllegalArgumentException> {
            VertexInputMapping(listOf(position, missing)).lower(geometry)
        }
        assertEquals("Unsupported format conversion for ${VertexSemantics.Position}", formatFailure.message)
        val missingFailure = assertFailsWith<IllegalArgumentException> {
            VertexInputMapping(listOf(missing, position)).lower(geometry)
        }
        assertEquals("Missing ${VertexSemantics.Normal}", missingFailure.message)
        val locationFailure = assertFailsWith<IllegalArgumentException> {
            VertexInputMapping(listOf(position.copy(format = VertexFormat.Float32x3))).lower(geometry)
        }
        assertEquals("Vertex attribute location must not be negative", locationFailure.message)
    }

    @Test
    fun generatedGeometryRequiresAnEmptyInputContract() {
        val geometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))

        assertSame(VertexState.Empty, VertexInputMapping(emptyList()).lower(geometry))
        val failure = assertFailsWith<IllegalArgumentException> {
            VertexInputMapping(listOf(AttributeInput(VertexSemantics.Position, 0, VertexFormat.Float32x3))).lower(geometry)
        }
        assertEquals("Shader-generated geometry cannot satisfy vertex attributes", failure.message)
    }

    private fun geometry(layout: GeometryLayout): VertexGeometry = VertexGeometry(layout, layout.streams.map { VertexStreamSource.Upload(ByteArray(0)) }, GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))
}
