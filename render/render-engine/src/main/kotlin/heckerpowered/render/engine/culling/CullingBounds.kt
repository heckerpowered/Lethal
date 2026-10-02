/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.culling

import heckerpowered.math.AffineTransformView
import heckerpowered.render.engine.view.Frustum

/**
 * Determines whether a contribution can be excluded from a view.
 *
 * A true result proves that every rendered position lies outside the queried frustum; false includes
 * intersection and insufficient information. Implementations retain stable conservative bounds
 * including animation or shader displacement. The supplied placement puts those local bounds in the world.
 */
fun interface CullingBounds {
    fun canCull(frustum: Frustum, localToWorld: AffineTransformView): Boolean
}
