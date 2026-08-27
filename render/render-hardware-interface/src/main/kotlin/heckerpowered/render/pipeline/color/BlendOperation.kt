/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

/**
 * Selects the arithmetic operation used to combine the factor-scaled source and
 * destination terms of a [BlendComponent].
 *
 * ```
 * sourceTerm      = source * sourceFactor
 * destinationTerm = destination * destinationFactor
 * result          = operation(sourceTerm, destinationTerm)
 * ```
 *
 * The operation is evaluated independently for each component. [BlendState.color]
 * applies it to red, green, and blue, while [BlendState.alpha] applies it separately to alpha.
 *
 * Blend operations do not inherently restrict their result to `[0, 1]`. A floating-point color
 * target can preserve negative values and values greater than one, while a normalized target
 * constrains and quantizes the result when it is stored.
 */
enum class BlendOperation {
    /**
     * Adds the factor-scaled source and destination terms:
     *
     * ```
     * result = source * sourceFactor + destination * destinationFactor
     * ```
     *
     * Despite its name, this operation is not limited to full-strength additive blending. The selected
     * factors determine whether it performs source-over transparency, premultiplied-alpha compositing,
     * additive accumulation, Multiply, Screen, replacement, or another weighted combination.
     *
     * Typical configurations include:
     *
     * - [BlendFactor.SourceAlpha] and [BlendFactor.OneMinusSourceAlpha] for straight-alpha RGB;
     * - [BlendFactor.One] and [BlendFactor.OneMinusSourceAlpha] for premultiplied-alpha RGB;
     * - [BlendFactor.One] for both terms when accumulating light, bloom, or glow;
     * - [BlendFactor.DestinationColor] and [BlendFactor.Zero] for Multiply;
     * - [BlendFactor.One] and [BlendFactor.OneMinusSourceColor] for Screen.
     *
     * Addition itself does not saturate the result. Values outside `[0, 1]` can be preserved by a
     * floating-point color target, while a normalized target constrains them when stored.
     *
     * Although the final addition is commutative, the complete blend equation generally is not:
     * source and destination factors may depend differently on the two inputs.
     */
    Add,

    /**
     * Subtracts the factor-scaled destination term from the factor-scaled source term:
     *
     * ```
     * result = source * sourceFactor - destination * destinationFactor
     * ```
     *
     * With [BlendFactor.One] for both factors, this computes `source - destination`. Typical uses
     * include storing signed differences or deltas in a floating-point target, and producing a
     * positive-difference mask in a normalized target where components below zero are discarded when
     * stored.
     *
     * This operation does not compute an absolute difference, and reversing source and destination
     * changes the result. In particular, it is not the usual choice for subtracting an incoming effect
     * from the existing target; use [ReverseSubtract] for `destination - source`.
     *
     * Negative results can be preserved by a floating-point color target but are constrained by an
     * unsigned-normalized target. When subtraction is required only for RGB, configure
     * [BlendState.alpha] separately so attachment alpha is not unintentionally subtracted as well.
     */
    Subtract,

    /**
     * Subtracts the factor-scaled source term from the factor-scaled destination term:
     *
     * ```
     * result = destination * destinationFactor - source * sourceFactor
     * ```
     *
     * With [BlendFactor.One] for both factors, this computes `destination - source`. It is useful when
     * the fragment output represents an amount to remove from an existing value, such as decrementing
     * an accumulation buffer or applying a subtractive mask.
     *
     * The removed amount can be weighted by another factor. For example, using
     * [BlendFactor.SourceAlpha] for the source term and [BlendFactor.One] for the destination term
     * subtracts the incoming RGB contribution according to its alpha.
     *
     * This is value subtraction, not proportional fading. To attenuate the existing destination by
     * source opacity, use [Add] with [BlendFactor.Zero] for the source term and
     * [BlendFactor.OneMinusSourceAlpha] for the destination term instead.
     *
     * Reverse subtraction is not a reliable way to undo an earlier addition. Clamping, quantization,
     * saturation, and floating-point rounding may already have discarded information.
     *
     * Negative results can be preserved by a floating-point color target but are constrained by an
     * unsigned-normalized target. When reverse subtraction is required only for RGB, configure
     * [BlendState.alpha] separately.
     */
    ReverseSubtract
}