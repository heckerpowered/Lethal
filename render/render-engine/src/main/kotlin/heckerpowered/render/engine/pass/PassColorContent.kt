/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.color.Color
import heckerpowered.render.command.pass.AttachmentLoadOperation
import heckerpowered.render.engine.image.RenderImageStore
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.ColorContent

/**
 * Resolves the alpha quantity of each color target from its declared meaning and owned images.
 *
 * Existing coverage cannot be reinterpreted as a numeric signal. Targets requiring coverage
 * must also begin with valid premultiplied coverage content, either through a compatible clear
 * operation or through previously established attachment contents.
 *
 * This validates the initial content contract, not whether subsequent shader writes preserve it.
 */
internal fun resolveTargetAlphaQuantities(pass: RasterPass, images: RenderImageStore?): Map<Int, AlphaQuantity> = buildMap {
    for (slot in pass.description.colorAttachments.indices) {
        val quantity = resolveTargetAlphaQuantity(pass, images, slot) ?: continue
        put(slot, quantity)
    }
}

private fun resolveTargetAlphaQuantity(pass: RasterPass, images: RenderImageStore?, slot: Int): AlphaQuantity? {
    val attachment = pass.description.colorAttachments[slot]?.attachment
    val storedQuantity = attachment?.let { images?.find(it)?.alphaQuantity }
    val resolvedQuantities = pass.description.colorResolves.filter { it.colorAttachment == slot }.mapNotNull { images?.find(it.destination)?.alphaQuantity }
    val requestedQuantity = pass.alphaQuantities[slot]
    val requiresCoverage = storedQuantity == AlphaQuantity.Coverage || AlphaQuantity.Coverage in resolvedQuantities

    require(!requiresCoverage || requestedQuantity != AlphaQuantity.Signal) { "A pass cannot weaken an owned coverage destination at slot $slot" }

    val quantity = if (requiresCoverage) AlphaQuantity.Coverage else requestedQuantity ?: storedQuantity ?: resolvedQuantities.firstOrNull()
    if (quantity == AlphaQuantity.Coverage) validateInitialCoverage(pass, slot, storedQuantity)
    return quantity
}

private fun validateInitialCoverage(pass: RasterPass, slot: Int, storedQuantity: AlphaQuantity?) {
    val attachment = pass.description.colorAttachments[slot] ?: return
    val hasAlpha = attachment.attachment.format.colorComponents.alpha

    when (val load = attachment.operation.load) {
        AttachmentLoadOperation.Discard -> throw IllegalArgumentException("Discard cannot establish coverage content without proven full attachment initialization")

        is AttachmentLoadOperation.Clear -> {
            if (!hasAlpha) return
            val representation = pass.initialColors[slot]?.representation ?: pass.clearRepresentation
            validateCoverageClear(load.value, representation)
        }

        AttachmentLoadOperation.Load -> {
            if (!hasAlpha) return
            validateCoverageLoad(pass.initialColors[slot], storedQuantity)
        }
    }
}

private fun validateCoverageClear(color: Color, representation: AlphaRepresentation?) {
    require(color.alpha.isFinite() && color.alpha in 0f..1f) { "Coverage clear alpha must be finite and in [0,1]" }

    val associationIndependent = color.alpha == 1f || (color.red == 0f && color.green == 0f && color.blue == 0f)
    require(associationIndependent || representation == AlphaRepresentation.Premultiplied) { "Declare premultiplied clear values or convert straight color before clearing coverage" }
}

private fun validateCoverageLoad(declared: ColorContent?, storedQuantity: AlphaQuantity?) {
    val valid = declared?.let { it.alphaQuantity == AlphaQuantity.Coverage && it.representation == AlphaRepresentation.Premultiplied }
        ?: (storedQuantity == AlphaQuantity.Coverage)

    require(valid) { "Declare premultiplied coverage loaded by this pass before producing or resolving coverage" }
}