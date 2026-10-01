/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.RenderPassResources
import java.util.*

/**
 * Ordered prepared direct draws together with the pass resource declaration implied by them.
 *
 * Preparation must finish before a render pass begins because [RenderPassResources] is an upper
 * bound supplied at pass entrance. This sequence makes that boundary explicit: its [resources]
 * can be passed to the encoder first, then [encode] reproduces the stored draw order inside the
 * active pass without rediscovering GPU accesses.
 *
 * No sorting, batching, or state deduplication is performed. Draw order can affect blending,
 * depth/stencil results, and shader-visible side effects, so optimization belongs to a later stage
 * that can prove the relevant ordering constraints.
 *
 * An empty sequence has [RenderPassResources.Empty]. It does not imply that the enclosing render
 * pass can be skipped; attachment clear, store, and resolve operations may still be observable.
 */
internal class PreparedDirectDrawSequence(draws: List<PreparedDirectDraw>) {
    val draws: List<PreparedDirectDraw> = Collections.unmodifiableList(ArrayList(draws))

    val resources: RenderPassResources = if (this.draws.isEmpty()) {
        RenderPassResources.Empty
    } else {
        RenderPassResources(
            descriptors = this.draws.flatMap { it.descriptorSets.values },
            vertexBuffers = this.draws.flatMap { it.vertexBuffers.values },
            indexBuffers = this.draws.mapNotNull { it.indexInput?.view },
        )
    }

    /** Encodes every prepared draw in its original order into an already active render pass. */
    fun encode(pass: RenderPass) {
        draws.forEach { it.encode(pass) }
    }
}
