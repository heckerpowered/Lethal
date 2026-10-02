/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.scene.RenderSubmissionList
import heckerpowered.render.engine.view.ViewParameters

/**
 * Draws a submission list into one RHI pass using a common view and rendering phase.
 *
 * The submissions retain local geometry, appearance, placement, and raster scope. Pass processing
 * dispatches each element to its semantic processor, which resolves those inputs into concrete
 * draw commands. A list can be used again with a different view or phase.
 *
 * [description] defines attachments and their load/store operations; [phase] selects the rendering
 * purpose the element processors must implement. An unsupported phase fails during preparation.
 * [requireReplaySafe] rejects shaders whose declared behavior does not permit repeating the draw
 * across phases; it does not infer safety from shader code.
 */
class RasterPass(
    val description: RenderPassDescription,
    val collection: RenderSubmissionList,
    val inputs: ViewParameters,
    val phase: RasterPassPhase = RasterPassPhase.Color,
    val requireReplaySafe: Boolean = false,
)
