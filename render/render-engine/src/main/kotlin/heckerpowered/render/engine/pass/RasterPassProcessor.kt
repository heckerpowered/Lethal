/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.resource.ResourceLifetime

/**
 * Resolves a raster pass's submissions into complete commands and a pass resource declaration.
 *
 * Each element processor interprets its own semantic domain. Their commands remain in submission
 * order, including when one element produces several commands. Upload collection is shared within
 * this pass and completes before the prepared pass is recorded.
 *
 * The supplied lifetime must remain open through GPU completion. An empty command list still
 * produces a pass so attachment load, store, and resolve operations retain their meaning.
 */
internal class RasterPassProcessor(
    private val device: GraphicsDevice,
    private val elements: RenderElementPassProcessors,
) {
    fun prepare(pass: RasterPass, lifetime: ResourceLifetime): PreparedRenderPass {
        val preparation = PassPreparation(device, lifetime)
        val commands = pass.collection.submissions.flatMap { elements.prepare(it, pass, preparation) }
        return PreparedRenderPass(pass.description, preparation.snapshot(), commands)
    }
}
