/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope
import heckerpowered.render.engine.support.collection.CopyOnWriteList

/**
 * Collects render-element occurrences while capturing their placement and raster settings.
 *
 * Elements stay in local space. Each submission receives its own context snapshot and collection
 * ordinal; transformation and pass selection happen during later preparation.
 *
 * [snapshot] returns an independent list without ending collection. Further submissions can be
 * collected and included in a later snapshot. Collection and snapshotting require external
 * synchronization if used from more than one thread.
 */
class RenderSubmissionCollector : RenderElementCollector {
    private val submissions = CopyOnWriteList<RenderSubmission>()

    context(context: ObjectSubmitContext)
    override fun submit(element: RenderElement) {
        collect(element, context)
    }

    internal fun collect(element: RenderElement, context: ObjectSubmitContext, rasterScope: RasterScope = RasterScope(), visibility: DepthMode = DepthMode.Scene) {
        submissions.add(RenderSubmission(element, context, rasterScope, visibility, submissions.size))
    }

    /** Captures the current membership and order; later submissions do not change the returned list. */
    fun snapshot(): RenderSubmissionList = RenderSubmissionList(submissions)
}
