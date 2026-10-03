/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.math.AffineTransforms
import kotlin.test.*

class RenderElementCollectorTest {
    private val placement = ObjectSubmitContext(AffineTransforms.Identity)

    @Test
    fun emptyArgumentsDoNotSubmitAnything() {
        val collector = RecordingCollector()
        with(placement) { collector.submit() }
        assertTrue(collector.elements.isEmpty())
        assertTrue(collector.contexts.isEmpty())
    }

    @Test
    fun singleVarargElementDelegatesWithTheSamePlacement() {
        val element = object : RenderElement {}
        val collector = RecordingCollector()
        with(placement) { collector.submit(*arrayOf(element)) }
        assertSame(element, collector.elements.single())
        assertSame(placement, collector.contexts.single())
    }

    @Test
    fun twoElementsKeepArgumentOrderAndPlacement() {
        val coreElement = object : RenderElement {}
        val shellElement = object : RenderElement {}
        val collector = RecordingCollector()
        with(placement) { collector.submit(coreElement, shellElement) }
        assertEquals(2, collector.elements.size)
        assertSame(coreElement, collector.elements[0])
        assertSame(shellElement, collector.elements[1])
        collector.contexts.forEach { assertSame(placement, it) }
    }

    @Test
    fun failurePropagatesWithoutSubmittingLaterElements() {
        val firstElement = object : RenderElement {}
        val rejectedElement = object : RenderElement {}
        val laterElement = object : RenderElement {}
        val failure = IllegalStateException("Rejected element")
        val attemptedElements = mutableListOf<RenderElement>()
        val collector = object : RenderElementCollector {
            context(context: ObjectSubmitContext)
            override fun submit(element: RenderElement) {
                assertSame(placement, context)
                attemptedElements.add(element)
                if (element === rejectedElement) throw failure
            }
        }
        val thrown = assertFailsWith<IllegalStateException> {
            with(placement) { collector.submit(firstElement, rejectedElement, laterElement) }
        }
        assertSame(failure, thrown)
        assertEquals(listOf(firstElement, rejectedElement), attemptedElements)
    }

    private class RecordingCollector : RenderElementCollector {
        val elements = mutableListOf<RenderElement>()
        val contexts = mutableListOf<ObjectSubmitContext>()

        context(context: ObjectSubmitContext)
        override fun submit(element: RenderElement) {
            elements.add(element)
            contexts.add(context)
        }
    }
}
