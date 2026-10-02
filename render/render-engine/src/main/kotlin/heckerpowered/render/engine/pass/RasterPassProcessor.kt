/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.pass.AttachmentLoadOperation
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.ColorContent
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
    private val images: RenderImageStore? = null,
) {
    fun prepare(pass: RasterPass, lifetime: ResourceLifetime): PreparedRenderPass {
        val targetAlphaQuantities = resolveTargetAlphaQuantities(pass)
        val preparation = PassPreparation(device, lifetime, targetAlphaQuantities, images)
        val commands = pass.collection.submissions.flatMap { elements.prepare(it, pass, preparation) }
        commands.forEach { validateBlendDestination(it.pipeline, pass.description) }

        return PreparedRenderPass(pass.description, preparation.snapshot(), commands)
    }

    private fun resolveTargetAlphaQuantities(pass: RasterPass): Map<Int, AlphaQuantity> {
        return pass.description.colorAttachments.indices.mapNotNull { slot ->
            resolveTargetAlphaQuantity(pass, slot)?.let { slot to it }
        }.toMap()
    }

    private fun resolveTargetAlphaQuantity(pass: RasterPass, slot: Int): AlphaQuantity? {
        val directImage = pass.description.colorAttachments[slot]?.attachment?.let { images?.find(it) }
        val resolvedImages = pass.description.colorResolves
            .filter { it.colorAttachment == slot }
            .mapNotNull { images?.find(it.destination) }
        val requiresCoverage = directImage?.alphaQuantity == AlphaQuantity.Coverage || resolvedImages.any { it.alphaQuantity == AlphaQuantity.Coverage }

        val requestedQuantity = pass.alphaQuantities[slot]
        require(!requiresCoverage || requestedQuantity != AlphaQuantity.Signal) { "A pass cannot weaken an owned coverage destination at slot $slot" }
        val targetQuantity = if (requiresCoverage) AlphaQuantity.Coverage
        else requestedQuantity ?: directImage?.alphaQuantity ?: resolvedImages.firstOrNull()?.alphaQuantity

        if (targetQuantity == AlphaQuantity.Coverage) validateInitialCoverage(pass, slot, directImage?.alphaQuantity)
        return targetQuantity
    }

    private fun validateInitialCoverage(pass: RasterPass, slot: Int, storedQuantity: AlphaQuantity?) {
        val use = pass.description.colorAttachments[slot] ?: return
        require(use.operation.load != AttachmentLoadOperation.Discard) { "Discard cannot establish coverage content without proven full attachment initialization" }
        if (!use.attachment.format.colorComponents.alpha) return
        when (val load = use.operation.load) {
            is AttachmentLoadOperation.Clear -> {
                val color = load.value
                require(color.alpha.isFinite() && color.alpha in 0f..1f) { "Coverage clear alpha must be finite and in [0,1]" }
                val independent = color.alpha == 1f || color.red == 0f && color.green == 0f && color.blue == 0f
                require(independent || (pass.initialColors[slot]?.representation ?: pass.clearRepresentation) == AlphaRepresentation.Premultiplied) { "Declare premultiplied clear values or convert straight color before clearing coverage" }
            }

            AttachmentLoadOperation.Load -> {
                val declared = pass.initialColors[slot] ?: if (storedQuantity == AlphaQuantity.Coverage)
                    ColorContent(AlphaQuantity.Coverage, AlphaRepresentation.Premultiplied) else null
                require(declared?.alphaQuantity == AlphaQuantity.Coverage && declared.representation == AlphaRepresentation.Premultiplied) { "Declare premultiplied coverage loaded by this pass before producing or resolving coverage" }
            }

            AttachmentLoadOperation.Discard -> error("Discard cannot establish coverage content without proven full attachment initialization")
        }
    }

}
