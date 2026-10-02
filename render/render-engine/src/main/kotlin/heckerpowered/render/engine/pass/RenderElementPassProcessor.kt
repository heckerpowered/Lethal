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
 * Converts one supported element into the concrete draws needed by a particular pass.
 *
 * An element may produce no draws or several draws. Returned order is significant; preparation
 * allocates uploads before recording, while submission placement and raster scope remain available
 * for interpreting local-space data. Processor implementations must preserve their element's semantics.
 */
internal fun interface RenderElementPassProcessor<T : RenderElement> {
    fun prepare(element: T, submission: RenderSubmission, pass: RasterPass, preparation: PassPreparation): List<PreparedDrawCommand>
}
