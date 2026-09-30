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
        // Producers may reuse mutable transform views while emitting multiple elements.
        // A submission must preserve the object state at this exact submit point rather
        // than observing later mutations during visibility or pass processing.
        submissions += RenderSubmission(element, context.snapshot())
    }
}