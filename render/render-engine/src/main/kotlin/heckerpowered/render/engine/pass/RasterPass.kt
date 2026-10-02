/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.ColorContent
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.support.collection.toUnmodifiableMap
import heckerpowered.render.engine.view.ViewParameters

/**
 * Draws a submission list into one color raster pass using a common view.
 *
 * The submissions retain local geometry, shading, placement, and raster scope. Pass processing
 * dispatches each element to its semantic processor, which resolves those inputs into concrete
 * draw commands. A list can be reused with different views and attachments.
 *
 * [description] defines attachments and their load/store operations. [requireReplaySafe] rejects
 * shaders whose declared behavior does not permit repeating the draw across passes; it does not
 * infer safety from shader code.
 *
 * TODO: OIT depth bounds, transmittance, accumulation, and outlines need concrete shaders and
 * pass processing before public helpers can schedule them.
 */
class RasterPass(
    val description: RenderPassDescription,
    val collection: RenderSubmissionList,
    val inputs: ViewParameters,
    val requireReplaySafe: Boolean = false,
    val clearRepresentation: AlphaRepresentation? = null,
    alphaQuantities: Map<Int, AlphaQuantity> = emptyMap(),
    initialColors: Map<Int, ColorContent> = emptyMap(),
) {
    val alphaQuantities: Map<Int, AlphaQuantity> = alphaQuantities.toUnmodifiableMap()
    val initialColors: Map<Int, ColorContent> = initialColors.toUnmodifiableMap()

    init {
        require((this.alphaQuantities.keys + this.initialColors.keys).all { description.colorAttachments.getOrNull(it) != null }) { "Color content declarations require active pass slots" }
    }
}
