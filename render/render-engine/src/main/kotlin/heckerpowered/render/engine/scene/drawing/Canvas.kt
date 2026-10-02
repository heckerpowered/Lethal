/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene.drawing

import heckerpowered.math.AffineTransforms
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.scene.*
import heckerpowered.render.engine.shader.program.MeshShading

/**
 * Collects flat geometry and images with scoped framebuffer clipping and no scene-depth test.
 *
 * Geometry is submitted at identity placement with [DepthMode.SeeThrough]. To use coordinates as
 * pixels, the consuming pass supplies a view that maps those coordinates into clip space; Canvas
 * itself does not create a projection, choose attachments, or execute a pass.
 *
 * Object renderers use the same collection path and inherit the active clip stack. Image and
 * geometry resource references must remain valid through preparation and execution.
 */
class Canvas private constructor(
    private val collector: RenderSubmissionCollector,
    private val context: ObjectSubmitContext,
    private val scope: RasterScope,
) : ObjectRenderCollector {
    override fun <S> submit(renderer: ObjectRenderer<S>, state: S) {
        val elements = ScopedRenderElementCollector(collector, scope, DepthMode.SeeThrough)
        with(context) { renderer.submit(state, elements) }
    }

    fun geometry(geometry: RenderGeometry, shading: MeshShading, composition: CompositingMode = CompositingMode.Replace) {
        collector.collect(GeometryElement(geometry, shading, composition), context, scope, DepthMode.SeeThrough)
    }

    /**
     * Places a rectangle using source-over compositing for the bound shader's alpha representation.
     * [uv] selects texture coordinates independently of the rectangle's drawing coordinates.
     */
    fun image(shading: MeshShading, rectangle: Rectangle, uv: Rectangle = Rectangle(0f, 0f, 1f, 1f)) {
        geometry(rectangleGeometry(rectangle, uv), shading, CompositingMode.SourceOver(shading.shader.sourceRepresentation))
    }

    /** Clips the block in framebuffer coordinates, intersecting any enclosing clips. */
    fun clip(rectangle: ScissorRectangle, block: Canvas.() -> Unit) {
        Canvas(collector, context, scope.copy(scissors = scope.scissors + rectangle)).block()
    }

    companion object {
        /** Runs the block once and captures its current submission membership and order. */
        fun collect(block: Canvas.() -> Unit): RenderSubmissionList {
            val collector = RenderSubmissionCollector()
            Canvas(collector, ObjectSubmitContext(AffineTransforms.Identity), RasterScope()).block()
            return collector.snapshot()
        }
    }
}
