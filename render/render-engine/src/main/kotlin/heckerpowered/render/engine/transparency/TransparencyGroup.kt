/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.transparency

import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList

/**
 * Keeps a collection within one transparency ordering boundary.
 *
 * A null [farDistance] preserves its submission order. Otherwise, the group must contain only
 * strategy-selectable translucent geometry and is sorted farthest first using that function.
 * Groups themselves remain in declaration order; sorting never moves a draw into another group.
 */
class TransparencyGroup(
    val surfaces: RenderSubmissionList,
    val farDistance: ((RenderSubmission) -> Double)? = null,
)
