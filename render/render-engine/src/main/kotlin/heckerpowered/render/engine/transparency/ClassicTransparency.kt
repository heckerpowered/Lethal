/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.transparency

import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.scene.geometryElement

/**
 * Resolves strategy-selectable translucency to ordinary source-over drawing.
 *
 * The caller chooses whether to preserve submission order or sort selected contributions from
 * farthest to nearest. [representation] supplies the alpha representation produced by each draw's
 * shader, so the resolved operator uses the matching blend equation.
 *
 * These operations accept geometry elements. Exact compositing requests are not permission to
 * reorder a draw: order-preserving resolution leaves them unchanged, and distance-sorted input
 * must contain only [CompositingMode.Translucent] requests.
 */
class ClassicTransparency(private val representation: (RenderSubmission) -> AlphaRepresentation = { it.geometryElement.shading.shader.sourceRepresentation }) {
    /** Resolves translucent requests without moving them or changing other compositing operators. */
    fun preserveOrder(input: RenderSubmissionList): RenderSubmissionList = RenderSubmissionList(input.submissions.map { submission ->
        if (submission.geometryElement.composition == CompositingMode.Translucent) sourceOver(submission) else submission
    })

    /** Concatenates groups in declaration order, applying only each group's selected ordering. */
    fun groups(input: List<TransparencyGroup>): RenderSubmissionList = RenderSubmissionList(input.flatMap { group ->
        val key = group.farDistance
        (if (key == null) preserveOrder(group.surfaces) else collection(group.surfaces, key)).submissions
    })

    /**
     * Resolves translucent geometry from largest finite distance to smallest.
     *
     * Equal distances are ordered by each submission's source order. The distance function chooses
     * the meaning of distance; neither a camera nor a geometric center is inferred here.
     */
    fun collection(input: RenderSubmissionList, farDistance: (RenderSubmission) -> Double): RenderSubmissionList {
        require(input.submissions.all { it.geometryElement.composition == CompositingMode.Translucent })
        val keyed = input.submissions.map { surface ->
            val distance = farDistance(surface)
            require(distance.isFinite())
            surface to distance
        }
        val ordered = keyed.sortedWith(compareByDescending<Pair<RenderSubmission, Double>> { it.second }
            .thenBy { it.first.sourceOrder })
        return RenderSubmissionList(ordered.map { [submission, _] -> sourceOver(submission) })
    }

    private fun sourceOver(submission: RenderSubmission): RenderSubmission {
        val element = submission.geometryElement
        val composition = CompositingMode.SourceOver(representation(submission))
        return submission.copy(element = element.copy(composition = composition))
    }
}
