/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.pass

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.AttachmentLoadOperation
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.pipeline.color.BlendComponent
import heckerpowered.render.pipeline.color.BlendFactor

/**
 * Discard defines no destination values. General geometry does not prove that earlier draws cover
 * every sample, so a discarded slot remains unavailable to destination-dependent blend equations.
 * Format-defined missing alpha is still one; it does not initialize physically stored RGB.
 */
internal fun validateBlendDestination(pipeline: RenderPipelineDescription, description: RenderPassDescription) {
    pipeline.colorTargets.forEachIndexed { slot, state ->
        val attachment = description.colorAttachments.getOrNull(slot) ?: return@forEachIndexed
        if (attachment.operation.load != AttachmentLoadOperation.Discard) return@forEachIndexed

        val blend = state.blend ?: return@forEachIndexed
        val stored = state.format.colorComponents
        val requested = state.writeMask
        val writesRGB = requested.red && stored.red || requested.green && stored.green || requested.blue && stored.blue
        val writesAlpha = requested.alpha && stored.alpha

        require(!writesRGB || !blend.color.readsDestination(stored.alpha)) { "Discarded RGB at slot $slot cannot be read by blending" }
        require(!writesAlpha || !blend.alpha.readsDestination(true)) { "Discarded alpha at slot $slot cannot be read by blending" }
    }
}

private fun BlendComponent.readsDestination(hasStoredAlpha: Boolean): Boolean {
    val destinationTermIsZero = destinationFactor == BlendFactor.Zero ||
            !hasStoredAlpha && destinationFactor == BlendFactor.OneMinusDestinationAlpha
    return !destinationTermIsZero || when (sourceFactor) {
        BlendFactor.DestinationColor, BlendFactor.OneMinusDestinationColor -> true
        BlendFactor.DestinationAlpha, BlendFactor.OneMinusDestinationAlpha -> hasStoredAlpha
        else -> false
    }
}
