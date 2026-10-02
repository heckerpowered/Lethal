/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.transparency

import heckerpowered.math.AffineTransforms
import heckerpowered.render.color.Color
import heckerpowered.render.engine.geometry.triangle
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import heckerpowered.render.engine.scene.geometryElement
import heckerpowered.render.engine.testSurfaceShaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClassicTransparencyTest {
    @Test
    fun resolvingTransparencyPreservesExactRequestsPlacementAndSourceElements() {
        val opaque = element(CompositingMode.Replace)
        val translucent = element(CompositingMode.Translucent)
        val input = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            submit(opaque)
            submit(translucent)
        }
        val resolved = ClassicTransparency { AlphaRepresentation.Premultiplied }.preserveOrder(input)
        assertSame(input.submissions[0], resolved.submissions[0])
        assertEquals(CompositingMode.SourceOver(AlphaRepresentation.Premultiplied), resolved.submissions[1].geometryElement.composition)
        assertEquals(CompositingMode.Translucent, translucent.composition)
        assertSame(translucent.geometry, resolved.submissions[1].geometryElement.geometry)
        assertEquals(input.submissions.map { it.sourceOrder }, resolved.submissions.map { it.sourceOrder })
    }

    @Test
    fun backToFrontSortingKeepsOriginalOrderForEqualDistances() {
        val input = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            repeat(3) { submit(element(CompositingMode.Translucent)) }
        }
        val distances = listOf(4.0, 7.0, 7.0)
        val resolved = ClassicTransparency { AlphaRepresentation.Straight }.collection(input) { distances[it.sourceOrder] }
        assertEquals(listOf(1, 2, 0), resolved.submissions.map { it.sourceOrder })
        assertTrue(resolved.submissions.all { it.geometryElement.composition == CompositingMode.SourceOver(AlphaRepresentation.Straight) })
    }

    private fun element(composition: CompositingMode): GeometryElement {
        val geometry = triangle(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        return GeometryElement(geometry, testSurfaceShaders.unlit.bind(Color.TransparentBlack), composition)
    }
}
