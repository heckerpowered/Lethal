/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.VertexAttributeFormat.Float2
import heckerpowered.render.VertexAttributeFormat.Float3
import heckerpowered.render.VertexAttributeFormat.Float4
import heckerpowered.render.VertexInputRate.Instance
import kotlin.test.Test
import kotlin.test.assertEquals

class VertexBufferLayoutTest {
    @Test
    fun dslCalculatesLocationsOffsetsAndStridesAcrossBindings() {
        val layout = vertexBufferLayout {
            binding {
                attribute(Float3, "position")
                attribute(Float3, "normal")
                attribute(Float2, "textureCoordinates")
            }
            binding(Instance) {
                attribute(Float4, "modelRow0")
                attribute(Float4, "modelRow1")
                attribute(Float4, "modelRow2")
                attribute(Float4, "modelRow3")
                attribute(Float4, "color")
            }
        }

        val vertexBinding = layout.bindings[0]
        assertEquals(expected = 32, actual = vertexBinding.strideBytes)
        assertEquals(expected = listOf(0, 1, 2), actual = vertexBinding.attributes.map(VertexAttribute::location))
        assertEquals(expected = listOf(0, 12, 24), actual = vertexBinding.attributes.map(VertexAttribute::offsetBytes))

        val instanceBinding = layout.bindings[1]
        assertEquals(expected = Instance, actual = instanceBinding.inputRate)
        assertEquals(expected = 80, actual = instanceBinding.strideBytes)
        assertEquals(expected = listOf(3, 4, 5, 6, 7), actual = instanceBinding.attributes.map(VertexAttribute::location))
        assertEquals(expected = listOf(0, 16, 32, 48, 64), actual = instanceBinding.attributes.map(VertexAttribute::offsetBytes))
    }
}
