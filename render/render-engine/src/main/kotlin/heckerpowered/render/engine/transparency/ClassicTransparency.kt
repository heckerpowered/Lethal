/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.transparency

import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList

/**
 * Orders contributions explicitly selected by the caller, preserving their shaders and output state.
 * A group bounds sorting independently; equal distances retain source order. Ordering never chooses
 * compositing or depth writes. Submit transparent scene contributions with explicit depthWrite=false.
 */
class ClassicTransparency {
    fun preserveOrder(input: RenderSubmissionList): RenderSubmissionList = input

    fun groups(input: List<TransparencyGroup>): RenderSubmissionList = RenderSubmissionList(input.flatMap { group ->
        val distance = group.farDistance
        (if (distance == null) group.surfaces else collection(group.surfaces, distance)).submissions
    })

    fun collection(input: RenderSubmissionList, farDistance: (RenderSubmission) -> Double): RenderSubmissionList {
        val distances = input.submissions.map { submission ->
            val distance = farDistance(submission)
            require(distance.isFinite()) { "Transparency sorting requires finite distances" }
            submission to distance
        }
        val sorted = distances.sortedWith(compareByDescending<Pair<RenderSubmission, Double>> { it.second }.thenBy { it.first.sourceOrder })
        return RenderSubmissionList(sorted.map { it.first })
    }
}
