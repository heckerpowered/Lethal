/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.pipeline.color.*

internal fun validateColorContent(outputs: Map<Int, FragmentOutput>, values: ParameterValues, colorTargets: List<ColorTargetState>, targets: Map<Int, AlphaQuantity>, compositions: Map<Int, CompositingMode>) {
    colorTargets.forEachIndexed { slot, state ->
        val present = state.format.colorComponents
        val mask = state.writeMask.intersect(present)
        if (mask == ColorWriteMask.None) return@forEachIndexed

        val declaration = requireNotNull(resolvedFragmentOutput(outputs, slot, values)) { "Shader must declare fragment value meaning at location $slot" }
        val source = declaration.alphaQuantity
        val destination = if (state.format.isColor && !present.alpha) AlphaQuantity.Coverage else targets[slot]
        if (destination != AlphaQuantity.Coverage) return@forEachIndexed

        val supportedWrite = coverageWriteIsSafe(state, mask, declaration.representation, source)
        val composition = compositions[slot] ?: CompositingMode.Replace
        require(supportedWrite || composition.preservesCoverage(declaration, state)) { "Final equation/mask at slot $slot needs a coverage-preservation contract" }
    }
}

private fun ColorWriteMask.intersect(present: ColorWriteMask): ColorWriteMask =
    ColorWriteMask(red && present.red, green && present.green, blue && present.blue, alpha && present.alpha)

private fun coverageWriteIsSafe(state: ColorTargetState, mask: ColorWriteMask, representation: AlphaRepresentation, source: AlphaQuantity): Boolean {
    // Missing stored alpha reads as one, so any stored RGB has the opaque association.
    if (!state.format.colorComponents.alpha) return true

    val present = state.format.colorComponents
    val writesColor = mask.red || mask.green || mask.blue
    val blend = state.blend
    val preserve = BlendComponent(BlendFactor.Zero, BlendFactor.One)
    val colorUnchanged = !writesColor || blend?.color == preserve
    val alphaUnchanged = !mask.alpha || blend?.alpha == preserve
    if (colorUnchanged && alphaUnchanged) return true
    if (source == AlphaQuantity.Signal) return false

    // A changed alpha can invalidate untouched RGB, and changed RGB may need a different alpha.
    val writesAllColor = (!present.red || mask.red) && (!present.green || mask.green) && (!present.blue || mask.blue)
    if (!mask.alpha || !writesAllColor) return false

    val safeColor = coverageColorWriteIsSafe(blend, representation)
    val safeAlpha = coverageAlphaWriteIsSafe(blend)
    return safeColor && safeAlpha
}

private fun coverageColorWriteIsSafe(blend: BlendState?, representation: AlphaRepresentation): Boolean = when {
    blend == null -> representation == AlphaRepresentation.Premultiplied
    blend.color == BlendState.StraightAlpha.color -> representation == AlphaRepresentation.Straight
    blend.color == BlendState.PremultipliedAlpha.color -> representation == AlphaRepresentation.Premultiplied
    else -> false
}

private fun coverageAlphaWriteIsSafe(blend: BlendState?): Boolean =
    blend == null || blend.alpha == BlendState.PremultipliedAlpha.alpha
