/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene.drawing


import heckerpowered.math.AffineTransformView
import heckerpowered.math.AffineTransforms
import heckerpowered.math.copyOf
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.scene.*

/**
 * Describes world-space contributions within a shared placement and raster scope.
 *
 * Geometry and object renderers work in the current local coordinate system. [transformed]
 * composes another placement for its block, while [clip], [withViewport], and [visibility] apply
 * scoped settings. Contributions capture those settings when submitted; leaving a block restores
 * the enclosing scope without modifying previously collected elements.
 *
 * These operations collect descriptions rather than encode GPU commands. [collect] returns the
 * contributions for a pass to prepare with its own view and attachments. Resource references in
 * those descriptions must remain valid through preparation and execution.
 */
class WorldDrawing private constructor(
    private val collector: RenderSubmissionCollector,
    private val objectState: ObjectSubmitContext,
    private val rasterScope: RasterScope,
    private val visibility: DepthMode,
) : ObjectRenderCollector {
    override fun <S> submit(renderer: ObjectRenderer<S>, state: S) {
        val elements = ScopedRenderElementCollector(collector, rasterScope, visibility)
        with(objectState) { renderer.submit(state, elements) }
    }

    fun geometry(element: GeometryElement) {
        submit(element)
    }

    fun submit(element: RenderElement) {
        collector.collect(element, objectState, rasterScope, visibility)
    }

    /**
     * Collects child-local contributions at `currentLocalToWorld * localToParent`.
     * The composed placement is snapshotted before the block runs; vertices remain in local space.
     */
    fun <R> transformed(localToParent: AffineTransformView, block: WorldDrawing.() -> R): R {
        val child = objectState.transformed(localToParent).snapshot()
        return WorldDrawing(collector, child, rasterScope, visibility).block()
    }

    /**
     * Runs the block once for each placement, in list order.
     *
     * All placements are copied before the first invocation. This repeats collection at different
     * transforms; it does not request a hardware-instanced draw or combine their draw arguments.
     */
    fun instances(placements: List<AffineTransformView>, block: WorldDrawing.() -> Unit) {
        val snapshots = placements.map(AffineTransforms::copyOf)
        snapshots.forEach { transformed(it, block) }
    }

    fun <R> withViewport(viewport: Viewport, block: WorldDrawing.() -> R): R {
        return WorldDrawing(collector, objectState, rasterScope.copy(viewport = viewport), visibility).block()
    }

    /** Adds a framebuffer-space clip intersected with the enclosing clips during pass preparation. */
    fun <R> clip(rectangle: ScissorRectangle, block: WorldDrawing.() -> R): R {
        return WorldDrawing(
            collector, objectState,
            rasterScope.copy(scissors = rasterScope.scissors + rectangle), visibility
        ).block()
    }

    fun <R> withStencilReference(reference: UByte, block: WorldDrawing.() -> R): R {
        return WorldDrawing(collector, objectState, rasterScope.copy(stencilReference = reference), visibility).block()
    }

    fun <R> visibility(mode: DepthMode, block: WorldDrawing.() -> R): R {
        return WorldDrawing(collector, objectState, rasterScope, mode).block()
    }

    companion object {
        /**
         * Runs the block at a snapshot of [context] and returns the contributions it collected.
         *
         * The initial scope uses scene depth behavior and the pass's viewport. The returned list
         * is independent of later collection, including submissions through a retained facade;
         * retaining a facade does not extend GPU-resource lifetimes.
         */
        fun collect(context: ObjectSubmitContext, block: WorldDrawing.() -> Unit): RenderSubmissionList {
            val collector = RenderSubmissionCollector()
            WorldDrawing(collector, context.snapshot(), RasterScope(), DepthMode.Scene).block()
            return collector.snapshot()
        }
    }
}
