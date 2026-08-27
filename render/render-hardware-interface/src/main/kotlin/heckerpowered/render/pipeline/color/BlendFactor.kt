/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline.color

/**
 * Selects a multiplier used to construct the source or destination term of a blend equation.
 *
 * Each entry resolves to a four-component factor:
 *
 * ```
 * (redFactor, greenFactor, blueFactor, alphaFactor)
 * ```
 *
 * When used by [BlendState.color], the red, green, and blue factor components scale the
 * corresponding color components. When used by [BlendState.alpha], only the alpha factor component
 * is used.
 *
 * The words “source” and “destination” in an entry name identify the value from which the factor is
 * derived:
 *
 * - source is the fragment-shader output for the current color target;
 * - destination is the value already present in that target.
 *
 * They do not restrict which term the factor may scale. For example, [SourceAlpha] may be used as
 * [BlendComponent.destinationFactor], causing the existing destination value to be scaled by the
 * incoming source alpha.
 *
 * Factor values are determined before [ColorTargetState.writeMask] is applied. A component can
 * therefore participate in factor calculation even when that component is excluded from the final
 * attachment write. In particular, source alpha may control RGB blending while alpha writes remain
 * disabled.
 *
 * The color-target format determines how stored destination values are exposed to blending.
 * Format-defined decoding occurs before factors are evaluated; for example, an sRGB destination is
 * linearized before its color or alpha values participate in blending. Missing destination color
 * components use the substitution values defined by the texture-format contract.
 *
 * This enumeration contains only self-contained factors derived from the source, destination,
 * zero, or one. Factors requiring a separately supplied blend constant or a secondary
 * fragment-shader output are not represented.
 *
 * See [BlendComponent] for the complete equation in which these factors are used. A
 * [BlendOperation] that defines its result without scaled terms may ignore both factors.
 */
enum class BlendFactor {
    /**
     * Uses `(0, 0, 0, 0)` as the blend factor. For an operation that applies blend factors, this causes
     * the selected source or destination term to make no contribution, effectively removing it from
     * the blend equation.
     *
     * When [BlendOperation.Add] is used, typical purposes include:
     *
     * - replacing an existing component with its source value, such as replacing attachment alpha
     *   while RGB is blended separately;
     * - retaining only a source-destination product for multiplicative color modulation;
     * - removing the source color contribution while using source alpha to attenuate or erase the
     *   existing destination.
     *
     * `Zero` still produces and writes a blend result. Use [ColorTargetState.writeMask] instead when
     * selected attachment components must remain unchanged.
     */
    Zero,

    /**
     * Uses `(1, 1, 1, 1)` as the blend factor. For an operation that applies blend factors, this leaves
     * the selected source or destination term unscaled before the operation is evaluated.
     *
     * When [BlendOperation.Add] is used, typical purposes include:
     *
     * - preserving the complete destination value while adding an incoming effect such as bloom,
     *   emission, or glow;
     * - using premultiplied source color directly without multiplying it by source alpha a second time;
     * - keeping source alpha unscaled in the alpha equation, avoiding the unintended
     *   `sourceAlpha * sourceAlpha` produced by [SourceAlpha];
     * - replacing an existing component with its source value when the destination term uses [Zero].
     *
     * `One` preserves the selected input term, not the final blend result. The other term and the blend
     * operation may still change the value that is written.
     */
    One,

    /**
     * Uses the source color `(Sr, Sg, Sb, Sa)` as the blend factor, scaling each component of the
     * selected term by the corresponding component of the fragment-shader output.
     *
     * This factor is commonly applied to the destination term, together with [Zero] for the source
     * term, to perform multiplicative color modulation. A white source preserves the destination,
     * black removes it, and intermediate source colors tint or darken it.
     *
     * When applied to the source term, each source component is multiplied by itself. This
     * component-wise squaring is usually not appropriate for conventional transparency.
     *
     * In an alpha equation, only `Sa` is used, making this factor equivalent to [SourceAlpha].
     * The factor is not inherently restricted to `[0, 1]`; floating-point color targets may expose
     * negative source values or values greater than one.
     */
    SourceColor,

    /**
     * Uses the component-wise inverse of the source color as the blend factor:
     *
     * ```
     * factor = (1-Sr, 1-Sg, 1-Sb, 1-Sa)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * (1 - source)
     * destinationFactor -> destination * (1 - source)
     * ```
     *
     * This factor is commonly applied to the destination term while the source term uses [One],
     * producing screen blending:
     *
     * ```
     * result = source + destination * (1 - source)
     *        = 1 - (1 - source) * (1 - destination)
     * ```
     *
     * For values in `[0, 1]`, black source components preserve the destination, while brighter source
     * components progressively lighten it.
     *
     * In an alpha equation, only `1-Sa` is used, making this factor equivalent to
     * [OneMinusSourceAlpha].
     *
     * Subtraction from one is not saturating. On a floating-point color target, source values outside
     * `[0, 1]` may therefore produce negative factors or factors greater than one.
     */
    OneMinusSourceColor,

    /**
     * Uses the source alpha for every component of the blend factor:
     *
     * ```
     * factor = (Sa, Sa, Sa, Sa)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * Sa
     * destinationFactor -> destination * Sa
     * ```
     *
     * This factor is commonly applied to the source RGB term when the fragment shader outputs
     * straight, non-premultiplied color. Together with [OneMinusSourceAlpha] on the destination term,
     * it weights the incoming color by its opacity.
     *
     * It is also commonly paired with [One] on the destination term for alpha-weighted additive
     * effects, allowing transparent parts of an effect to contribute less while preserving the
     * existing destination color.
     *
     * When applied to the source term of the alpha equation, it produces `Sa * Sa`. This is usually not
     * the intended source-over alpha behavior; use [One] when source alpha should enter that equation
     * without being multiplied by itself.
     *
     * In an alpha equation, this factor is equivalent to [SourceColor].
     */
    SourceAlpha,

    /**
     * Uses the inverse source alpha for every component of the blend factor:
     *
     * ```
     * factor = (1-Sa, 1-Sa, 1-Sa, 1-Sa)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * (1 - Sa)
     * destinationFactor -> destination * (1 - Sa)
     * ```
     *
     * This factor is most commonly applied to the destination term in source-over compositing. It
     * preserves the destination where the source is transparent and progressively removes its
     * contribution as the source becomes opaque.
     *
     * For straight source color:
     *
     * ```
     * sourceFactor      = SourceAlpha
     * destinationFactor = OneMinusSourceAlpha
     *
     * result.rgb = source.rgb * source.a +
     *              destination.rgb * (1 - source.a)
     * ```
     *
     * For premultiplied source color:
     *
     * ```
     * sourceFactor      = One
     * destinationFactor = OneMinusSourceAlpha
     *
     * result.rgb = source.rgb +
     *              destination.rgb * (1 - source.a)
     * ```
     *
     * The conventional source-over alpha equation also uses [One] for the source term:
     *
     * ```
     * result.a = source.a + destination.a * (1 - source.a)
     * ```
     *
     * In an alpha equation, this factor is equivalent to [OneMinusSourceColor].
     *
     * Subtraction from one is not saturating. On a floating-point color target, source alpha outside
     * `[0, 1]` may produce a negative factor or a factor greater than one.
     */
    OneMinusSourceAlpha,

    /**
     * Uses the existing destination color as the blend factor:
     *
     * ```
     * factor = (Dr, Dg, Db, Da)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * destination
     * destinationFactor -> destination * destination
     * ```
     *
     * This factor is commonly applied to the source term while the destination term uses [Zero],
     * producing component-wise Multiply compositing:
     *
     * ```
     * result = source * destination + destination * 0
     *        = source * destination
     * ```
     *
     * This is mathematically equivalent to using [SourceColor] on the destination term while the source
     * term uses [Zero]. A multiplicative accumulation target is normally initialized to white, because
     * white is the neutral value for multiplication while black removes all subsequent contributions.
     *
     * Applying this factor to the destination term instead squares every destination component, which
     * is usually not the intended behavior.
     *
     * In an alpha equation, only `Da` is used, making this factor equivalent to [DestinationAlpha].
     * On a floating-point color target, the factor may be negative or greater than one.
     */
    DestinationColor,

    /**
     * Uses the component-wise inverse of the existing destination color as the blend factor:
     *
     * ```
     * factor = (1-Dr, 1-Dg, 1-Db, 1-Da)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * (1 - destination)
     * destinationFactor -> destination * (1 - destination)
     * ```
     *
     * This factor is commonly applied to the source term while the destination term uses [One],
     * producing screen blending:
     *
     * ```
     * result = source * (1 - destination) + destination
     *        = 1 - (1 - source) * (1 - destination)
     * ```
     *
     * Dark destination components admit more of the source contribution, while brighter destination
     * components progressively suppress it. A screen-accumulation target is therefore normally
     * initialized to black, which is the neutral destination value for this equation.
     *
     * Applying this factor to the destination term instead produces
     * `destination * (1 - destination)`, which is not the conventional screen equation.
     *
     * In an alpha equation, only `1-Da` is used, making this factor equivalent to
     * [OneMinusDestinationAlpha].
     *
     * Subtraction from one is not saturating. On a floating-point color target, destination values
     * outside `[0, 1]` may produce negative factors or factors greater than one.
     */
    OneMinusDestinationColor,

    /**
     * Uses the destination alpha for every component of the blend factor:
     *
     * ```
     * factor = (Da, Da, Da, Da)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * Da
     * destinationFactor -> destination * Da
     * ```
     *
     * This factor is commonly applied to the source term while the destination term uses [Zero],
     * producing Porter-Duff Source In compositing:
     *
     * ```
     * result = source * destination.a
     * ```
     *
     * The source is retained only where the destination already has coverage. This is useful for
     * drawing content through a mask or restricting a new layer to the shape accumulated in the
     * destination alpha.
     *
     * Coverage-based compositing with this factor normally assumes premultiplied color. The factor
     * supplies destination alpha only; it does not additionally multiply straight source RGB by source
     * alpha.
     *
     * Applying this factor to the destination term scales the destination by its own alpha. In the
     * alpha equation this produces `Da * Da`, which is usually not intended.
     *
     * Destination alpha remains available to factor calculation even when alpha is excluded by
     * [ColorTargetState.writeMask]. Its value must therefore have been initialized and maintained
     * meaningfully by earlier clears and draws.
     *
     * If the color-target format has no alpha component, destination alpha is substituted with one,
     * making this factor equivalent to [One].
     *
     * In an alpha equation, this factor is equivalent to [DestinationColor]. On a floating-point color
     * target, the factor may be negative or greater than one.
     */
    DestinationAlpha,

    /**
     * Uses the inverse destination alpha for every component of the blend factor:
     *
     * ```
     * factor = (1-Da, 1-Da, 1-Da, 1-Da)
     * ```
     *
     * Depending on which term uses this factor:
     *
     * ```
     * sourceFactor      -> source * (1 - Da)
     * destinationFactor -> destination * (1 - Da)
     * ```
     *
     * This factor is commonly applied to the source term while the destination term uses [One],
     * producing Porter-Duff Destination Over compositing:
     *
     * ```
     * result = source * (1 - destination.a) + destination
     * ```
     *
     * The incoming source contributes only where the existing destination is not already covered,
     * making the source appear behind the destination. Pairing this source factor with [Zero] for the
     * destination term instead retains only the part of the source outside the destination coverage.
     *
     * These coverage-compositing uses normally assume premultiplied color. With straight source RGB,
     * this factor does not apply the additional source-alpha weighting required by that representation.
     *
     * Applying this factor to the destination term produces
     * `destination * (1 - destination.a)`, which is not the conventional Destination Over equation.
     *
     * If the color-target format has no alpha component, destination alpha is substituted with one,
     * making this factor equivalent to [Zero].
     *
     * In an alpha equation, this factor is equivalent to [OneMinusDestinationColor].
     *
     * Subtraction from one is not saturating. On a floating-point color target, destination alpha
     * outside `[0, 1]` may produce a negative factor or a factor greater than one.
     */
    OneMinusDestinationAlpha,
}