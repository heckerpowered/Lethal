/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

import java.util.*

/**
 * Retains an ordered collection of contributions for later pass preparation.
 *
 * Membership is an immutable snapshot, and each submission already captures its placement and
 * raster scope. The same list can be prepared for another view without rerunning object
 * renderers. Pass strategies still decide whether a shader's replay contract permits multiple phases.
 *
 * Element data and GPU resources are not copied. They must remain valid for every preparation
 * and execution that consumes this list.
 */
class RenderSubmissionList internal constructor(submissions: List<RenderSubmission>) {
    val submissions: List<RenderSubmission> = Collections.unmodifiableList(ArrayList(submissions))
}
