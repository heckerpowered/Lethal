/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

internal class CollectingRenderElementCollector : RenderElementCollector {
    val submissions: List<RenderSubmission>
        field = ArrayList<RenderSubmission>()

    context(context: ObjectSubmitContext)
    override fun submit(element: RenderElement) {
        submissions += RenderSubmission(element, context = context)
    }
}