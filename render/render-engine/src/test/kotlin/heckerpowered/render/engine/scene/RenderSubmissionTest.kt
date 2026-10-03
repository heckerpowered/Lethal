/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.math.AffineTransforms
import heckerpowered.math.Vector
import heckerpowered.render.color.Color
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.engine.geometry.triangle
import heckerpowered.render.engine.shader.program.SurfaceColor
import heckerpowered.render.engine.testUnlitShader
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope
import heckerpowered.render.engine.scene.drawing.WorldDrawing
import kotlin.test.*

class RenderSubmissionTest {
    @Test
    fun objectRendererInheritsPlacementAndRasterScopeWithoutChangingLocalElements() {
        val element = testUnlitShader.bind(SurfaceColor(triangle(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), Color.TransparentBlack))
        val producer = object : ObjectRenderer<GeometryElement> {
            context(context: ObjectSubmitContext)
            override fun submit(state: GeometryElement, collector: RenderElementCollector) {
                collector.submit(state)
                with(context.transformed(AffineTransforms.fromTranslation(Vector(3.0, 0.0, 0.0)))) { collector.submit(state) }
            }
        }
        val rectangle = ScissorRectangle(1, 2, 3, 4)
        val root = ObjectSubmitContext(AffineTransforms.fromTranslation(Vector(10.0, 0.0, 0.0)))
        val submissions = WorldDrawing.collect(root) {
            transformed(AffineTransforms.fromTranslation(Vector(2.0, 0.0, 0.0))) {
                clip(rectangle) { visibility(DepthMode.AlwaysOnTop) { submit(producer, element) } }
            }
            submit(producer, element)
        }.submissions
        assertEquals(listOf(12.0, 15.0, 10.0, 13.0), submissions.map { it.objectState.localToWorld.translation.x })
        submissions.forEach { assertSame(element, it.element) }
        assertEquals(listOf(rectangle), submissions[0].rasterScope.scissors)
        assertEquals(DepthMode.AlwaysOnTop, submissions[0].visibility)
        assertTrue(submissions[2].rasterScope.scissors.isEmpty())
        assertEquals(DepthMode.Scene, submissions[2].visibility)
        assertEquals(listOf(0, 1, 2, 3), submissions.map { it.sourceOrder })
    }

    @Test
    fun canvasObjectSubmissionKeepsItsDepthAndClipScope() {
        val element = object : RenderElement {}
        val producer = object : ObjectRenderer<RenderElement> {
            context(context: ObjectSubmitContext)
            override fun submit(state: RenderElement, collector: RenderElementCollector) { collector.submit(state) }
        }
        val rectangle = ScissorRectangle(2, 3, 4, 5)
        val submission = heckerpowered.render.engine.scene.drawing.Canvas.collect { clip(rectangle) { submit(producer, element) } }.submissions.single()
        assertSame(element, submission.element)
        assertEquals(DepthMode.SeeThrough, submission.visibility)
        assertEquals(listOf(rectangle), submission.rasterScope.scissors)
        assertTrue(submission.objectState.localToWorld.isIdentity())
    }

    @Test
    fun canvasImageBindsItsRectangleAndPreservesClipDepthAndShaderAlphaPolicy() {
        var rectangleGeometry: heckerpowered.render.engine.geometry.VertexGeometry? = null
        val clip = ScissorRectangle(2, 3, 4, 5)
        val rectangle = heckerpowered.render.engine.scene.drawing.Rectangle(10f, 20f, 30f, 40f)
        val submission = heckerpowered.render.engine.scene.drawing.Canvas.collect {
            clip(clip) {
                image(rectangle) { geometry ->
                    rectangleGeometry = geometry
                    testUnlitShader.bind(SurfaceColor(geometry, Color.TransparentBlack))
                }
            }
        }.submissions.single()
        val element = submission.geometryElement
        assertSame<heckerpowered.render.engine.geometry.RenderGeometry>(assertNotNull(rectangleGeometry), element.geometry)
        assertSame(testUnlitShader, element.shading.shader)
        assertEquals(heckerpowered.render.engine.material.CompositingMode.SourceOver, element.composition)
        assertEquals(listOf(clip), submission.rasterScope.scissors)
        assertEquals(DepthMode.SeeThrough, submission.visibility)
        assertTrue(submission.objectState.localToWorld.isIdentity())
    }

    @Test
    fun reusableElementKeepsLocalGeometryAndPlacementIsAppliedOnce() {
        val element = testUnlitShader.bind(SurfaceColor(triangle(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), Color.TransparentBlack))
        val context = ObjectSubmitContext(AffineTransforms.fromTranslation(Vector(10.0, 0.0, 0.0)))
        val collected = WorldDrawing.collect(context) {
            submit(element)
            transformed(AffineTransforms.fromTranslation(Vector(2.0, 0.0, 0.0))) { submit(element) }
        }
        assertSame(element, collected.submissions[0].element)
        assertSame(element, collected.submissions[1].element)
        assertEquals(10.0, collected.submissions[0].objectState.localToWorld.translation.x)
        assertEquals(12.0, collected.submissions[1].objectState.localToWorld.translation.x)
        assertEquals(listOf(0, 1), collected.submissions.map { it.sourceOrder })
    }

    @Test
    fun rasterStateAndReturnedListRemainSnapshotsWhenDrawingContinues() {
        val element = object : RenderElement {}
        val clips = mutableListOf(ScissorRectangle(1, 2, 3, 4))
        val submission = RenderSubmission(element, ObjectSubmitContext(AffineTransforms.Identity), RasterScope(scissors = clips))
        clips.clear()
        assertEquals(1, submission.rasterScope.scissors.size)
        lateinit var escaped: WorldDrawing
        val collected = WorldDrawing.collect(ObjectSubmitContext(AffineTransforms.Identity)) {
            escaped = this
            clip(submission.rasterScope.scissors.single()) { visibility(DepthMode.SeeThrough) { submit(element) } }
        }
        assertEquals(DepthMode.SeeThrough, collected.submissions.single().visibility)
        assertEquals(submission.rasterScope.scissors, collected.submissions.single().rasterScope.scissors)
        escaped.submit(element)
        assertEquals(1, collected.submissions.size)
    }

    @Test
    fun collectorSnapshotsKeepTheirOrderAndReuseUnchangedMembership() {
        val collector = RenderSubmissionCollector()
        val context = ObjectSubmitContext(AffineTransforms.Identity)
        val element = object : RenderElement {}
        val empty = collector.snapshot()
        assertSame(empty.submissions, collector.snapshot().submissions)

        with(context) { collector.submit(element) }
        val first = collector.snapshot()
        val iterator = first.submissions.iterator()
        assertSame(first.submissions, collector.snapshot().submissions)
        with(context) {
            collector.submit(element)
            collector.submit(element)
        }
        val second = collector.snapshot()

        assertTrue(empty.submissions.isEmpty())
        assertEquals(1, first.submissions.size)
        assertEquals(listOf(0, 1, 2), second.submissions.map { it.sourceOrder })
        second.submissions.forEach { assertSame(element, it.element) }
        assertSame(second.submissions, collector.snapshot().submissions)
        assertSame(first.submissions.single(), iterator.next())
        assertFalse(iterator.hasNext())
        assertFailsWith<UnsupportedOperationException> { (second.submissions as MutableList<RenderSubmission>).clear() }
    }

    @Test
    fun submissionListCopiesExternalMembershipWhileRetainingOccurrences() {
        val element = object : RenderElement {}
        val submission = RenderSubmission(element, ObjectSubmitContext(AffineTransforms.Identity))
        val source = arrayListOf(submission)
        val snapshot = RenderSubmissionList(source)
        source.clear()

        assertSame(submission, snapshot.submissions.single())
        assertSame(element, snapshot.submissions.single().element)
        assertFailsWith<UnsupportedOperationException> { (snapshot.submissions as MutableList<RenderSubmission>).add(submission) }
    }
}
