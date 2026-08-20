/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

import heckerpowered.render.BlendFactor
import heckerpowered.render.BlendOperation

/**
 * Describes one factor-based equation used to combine a fragment output with the existing value of
 * a color target.
 *
 * The source value is produced by the fragment shader. The destination value is the value already
 * present in the color target:
 *
 * ```
 * sourceTerm      = source      * sourceFactor
 * destinationTerm = destination * destinationFactor
 * result          = operation(sourceTerm, destinationTerm)
 * ```
 *
 * A factor's position identifies the term it scales, not the value from which the factor is
 * derived. The source factor may depend on the destination value, and the destination factor may
 * depend on the source value.
 *
 * Multiplication and the blend operation are applied independently to each affected component.
 * [BlendState.color] applies this equation to red, green, and blue, while [BlendState.alpha]
 * applies it separately to alpha.
 *
 * Reusing an RGB equation for alpha can produce an unintended result. For example, using
 * `SourceAlpha` as the source factor of the alpha equation produces `sourceAlpha * sourceAlpha`.
 * Conventional source-over alpha accumulation instead uses `One` for the source alpha factor.
 *
 * [ColorTargetState.writeMask] is applied after blending. A component excluded from the write mask
 * therefore remains available as an input to blend factors even though the resulting value of that
 * component is not stored.
 */
data class BlendComponent(
    val sourceFactor: BlendFactor,
    val destinationFactor: BlendFactor,
    val operation: BlendOperation = BlendOperation.Add,
)