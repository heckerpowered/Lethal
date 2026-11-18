/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.particle

import heckerpowered.render.engine.scene.ObjectRenderer
import heckerpowered.render.engine.scene.ObjectSubmitContext
import heckerpowered.render.engine.scene.RenderElementCollector
import heckerpowered.render.engine.support.collection.toUnmodifiableList

/**
 * Submits the emitters of one effect in the order supplied at construction.
 *
 * The system keeps a snapshot of the emitter list, not a shared effect clock. On each submission,
 * every emitter receives the same state and collector and decides independently whether its
 * drawing is active. Submission order does not prescribe the order of the eventual GPU draws.
 *
 * @param S the rendering state shared by the effect's emitters.
 * @property label a nonblank name for the effect.
 * @property emitters the nonempty, read-only sequence used for submission.
 */
class ParticleSystem<S>(val label: String, emitters: List<ParticleEmitter<S>>) : ObjectRenderer<S> {
    val emitters: List<ParticleEmitter<S>> = emitters.toUnmodifiableList()

    init {
        require(label.isNotBlank())
        require(this.emitters.isNotEmpty())
    }

    context(context: ObjectSubmitContext)
    override fun submit(state: S, collector: RenderElementCollector) {
        for (emitter in emitters) emitter.submit(state, collector)
    }
}
