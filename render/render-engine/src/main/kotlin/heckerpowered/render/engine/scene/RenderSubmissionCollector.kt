/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.scene.drawing.RasterScope

/** Captures submission context once; element data remains in local space. */
class RenderSubmissionCollector : RenderElementCollector {
    private val submissions = ArrayList<RenderSubmission>()

    context(context: ObjectSubmitContext)
    override fun submit(element: RenderElement) {
        collect(element, context)
    }

    internal fun collect(
        element: RenderElement, context: ObjectSubmitContext,
        rasterScope: RasterScope = RasterScope(), visibility: DepthMode = DepthMode.Scene,
    ) {
        submissions += RenderSubmission(element, context, rasterScope, visibility, submissions.size)
    }

    fun snapshot(): RenderSubmissionList = RenderSubmissionList(submissions)
}
