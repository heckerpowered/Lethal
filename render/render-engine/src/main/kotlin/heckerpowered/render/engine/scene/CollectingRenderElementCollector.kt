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
        // Submission may outlive the producer's current mutable render state.
        // Snapshot the object context here so later visibility, sorting, and pass
        // processing observe the transform exactly as it was when submitted.
        submissions += RenderSubmission(element, context.snapshot())
    }
}