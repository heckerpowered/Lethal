/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.AffineTransformView
import heckerpowered.render.engine.culling.CullingBounds
import heckerpowered.render.engine.view.ViewParameters

/**
 * Asks the contribution's conservative culling capability whether this view can exclude it.
 * Without that capability, or when exclusion cannot be proven, the submission remains eligible.
 * This is frustum rejection, not an occlusion or depth test.
 */
fun CullingBounds?.visible(view: ViewParameters, localToWorld: AffineTransformView): Boolean {
    if (this == null) return true
    return !canCull(view.frustum, localToWorld)
}
