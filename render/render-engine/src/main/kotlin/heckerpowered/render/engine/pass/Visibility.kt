/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.geometryElement
import heckerpowered.render.engine.view.ViewParameters

/**
 * Asks the contribution's conservative culling capability whether this view can exclude it.
 * Without that capability, or when exclusion cannot be proven, the submission remains eligible.
 * This is frustum rejection, not an occlusion or depth test.
 */
fun visible(submission: RenderSubmission, inputs: ViewParameters): Boolean {
    val bounds = submission.geometryElement.cullingBounds ?: return true
    return !bounds.canCull(inputs.frustum, submission.objectState.localToWorld)
}
