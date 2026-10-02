/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.engine.draw.PreparedDrawCommand
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.scene.RenderElement
import heckerpowered.render.engine.scene.RenderSubmission

/**
 * Selects preparation behavior by the concrete class of a submitted render element.
 *
 * Dispatch uses exact class identity, not inheritance matching. Each class has one installed
 * processor; submitting an unregistered element fails during preparation. This keeps the element's
 * own interpretation intact instead of forcing every contribution into a shared geometry wrapper.
 */
internal class RenderElementPassProcessors {
    private val processors = LinkedHashMap<Class<out RenderElement>, (RenderSubmission, RasterPass, PassPreparation) -> List<PreparedDrawCommand>>()

    fun <T : RenderElement> install(type: Class<T>, processor: RenderElementPassProcessor<T>) {
        require(type !in processors) { "A pass processor is already registered for ${type.name}" }
        processors[type] = { submission, pass, preparation -> processor.prepare(type.cast(submission.element), submission, pass, preparation) }
    }

    internal fun prepare(submission: RenderSubmission, pass: RasterPass, preparation: PassPreparation): List<PreparedDrawCommand> {
        val processor = requireNotNull(processors[submission.element.javaClass]) { "No pass processor for ${submission.element.javaClass.name}" }
        return processor(submission, pass, preparation)
    }
}
