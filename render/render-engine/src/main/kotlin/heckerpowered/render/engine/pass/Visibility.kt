/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.BoxView
import heckerpowered.math.transformedBy
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.geometryElement
import heckerpowered.render.engine.view.ViewParameters

/**
 * Tests a geometry submission's explicitly supplied conservative bounds against the view frustum.
 *
 * Bounds are transformed from local into world space before testing. Without a bound, the
 * submission remains eligible; no bound is inferred from vertex buffers or shader behavior.
 * This is frustum rejection, not an occlusion or depth test.
 */
fun visible(submission: RenderSubmission, inputs: ViewParameters): Boolean =
    visible(submission.geometryElement.cullingBounds?.bounds, submission.objectState, inputs)

internal fun visible(bounds: BoxView?, context: ObjectSubmitContext, inputs: ViewParameters): Boolean {
    val bound = bounds ?: return true
    return inputs.frustum.intersects(bound.transformedBy(context.localToWorld))
}
