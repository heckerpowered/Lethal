/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.pipeline.color.BlendComponent
import heckerpowered.render.pipeline.color.BlendFactor
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.texture.TextureFormat

/**
 * Describes enabled factor-based blending for one color target.
 *
 * [color] combines the fragment-shader RGB output with the existing destination RGB value, while
 * [alpha] independently determines the alpha value written to the target. Both equations are
 * evaluated for every affected color sample.
 *
 * A blend state does not declare whether the fragment shader produces straight or premultiplied
 * color. Its factors must match the representation of the shader output; using a straight-alpha
 * equation with premultiplied RGB, or the reverse, produces incorrect edge colors.
 *
 * Every [BlendState] instance represents enabled blending. Set [ColorTargetState.blend] to `null`
 * to disable blending, in which case the fragment output replaces the destination for components
 * enabled by [ColorTargetState.writeMask].
 *
 * Blending requires the color-target format to support attachment blending. The presence of a
 * format in [TextureFormat] does not by itself guarantee that capability.
 */
data class BlendState(
    /**
     * Blend equation applied independently to the red, green, and blue components.
     *
     * This equation may still use alpha-based factors such as [BlendFactor.SourceAlpha].
     */
    val color: BlendComponent,

    /**
     * Blend equation applied to the alpha component.
     *
     * This controls the stored alpha result; it does not prevent source or destination alpha from
     * being used as a factor by [color].
     */
    val alpha: BlendComponent,
) {
    companion object {
        /**
         * Performs source-over compositing for fragment output whose RGB components have not been multiplied
         * by alpha.
         *
         * ```
         * resultColor = sourceColor * sourceAlpha + destinationColor * (1 - sourceAlpha)
         * resultAlpha = sourceAlpha + destinationAlpha * (1 - sourceAlpha)
         * ```
         *
         * The source color is multiplied by source alpha exactly once during blending. Use this preset when
         * the fragment shader outputs straight, non-premultiplied color; applying it to color that was
         * already multiplied by alpha would multiply alpha twice and produce dark edges.
         *
         * The alpha equation uses [BlendFactor.One] for the source term rather than
         * [BlendFactor.SourceAlpha], so source alpha contributes as `sourceAlpha` instead of
         * `sourceAlpha * sourceAlpha`. The resulting alpha represents the union of source and destination
         * coverage.
         *
         * This preset is commonly suitable for sprites, particles, user-interface elements, and other
         * directly rendered content whose shader output combines ordinary color with a separate opacity.
         *
         * Although the incoming color is straight, source-over compositing writes an alpha-weighted result.
         * When rendering into a transparent intermediate target, the accumulated destination should
         * therefore be treated as premultiplied color. This equation does not convert the result back into
         * straight color, which would require division by `resultAlpha`.
         *
         * Straight-alpha textures are also more sensitive to filtering around transparent edges because
         * color from transparent texels is interpolated before the blend-time alpha multiplication. Prefer
         * [PremultipliedAlpha] for repeatedly filtered or composited transparent content.
         *
         * Source alpha is expected to represent opacity or coverage in `[0, 1]`. Floating-point targets do
         * not make factors outside that range behave like ordinary transparency.
         */
        val StraightAlpha = BlendState(
            BlendComponent(BlendFactor.SourceAlpha, BlendFactor.OneMinusSourceAlpha),
            BlendComponent(BlendFactor.One, BlendFactor.OneMinusSourceAlpha)
        )

        /**
         * Performs source-over compositing for fragment output whose RGB components have already been
         * multiplied by source alpha.
         *
         * ```
         * resultColor = sourceColor + destinationColor * (1 - sourceAlpha)
         * resultAlpha = sourceAlpha + destinationAlpha * (1 - sourceAlpha)
         * ```
         *
         * Unlike [StraightAlpha], this preset uses [BlendFactor.One] for the source color term because
         * `sourceColor` already contains its alpha weighting. Multiplying it by source alpha again would
         * produce dark and overly transparent edges.
         *
         * When the destination contains transparency, its color must also be premultiplied. Given correctly
         * premultiplied source and destination values, this equation produces another premultiplied result,
         * allowing transparent layers to be composited repeatedly without changing representation.
         *
         * Premultiplied color is particularly useful for filtered sprites, particles, user-interface
         * layers, and transparent intermediate targets. Interpolating premultiplied color prevents hidden
         * RGB values in transparent texels from bleeding into partially transparent edges.
         *
         * Multiplying by alpha in the fragment shader is sufficient to satisfy this blend equation, but it
         * cannot undo interpolation that already occurred on straight-alpha texture data. To obtain the
         * filtering benefit, texture colors must be premultiplied before texture filtering.
         *
         * Premultiplication must occur in the linear color domain used by blending:
         *
         * ```
         * premultipliedColor = linearColor * alpha
         * ```
         *
         * For sRGB source data, decode RGB to linear values before multiplying by alpha. If the result is
         * stored again in an sRGB texture, encode the premultiplied linear RGB afterward.
         *
         * Because the source color factor is [BlendFactor.One], `sourceAlpha == 0` does not by itself remove
         * a nonzero source color contribution. Ordinary fully transparent premultiplied texels must
         * therefore have zero RGB. A nonzero RGB value with zero alpha remains visible and should only be
         * used deliberately, such as for an emissive contribution that does not add coverage.
         *
         * Use [StraightAlpha] when the shader instead outputs RGB independently of alpha.
         */
        val PremultipliedAlpha = BlendState(
            BlendComponent(BlendFactor.One, BlendFactor.OneMinusSourceAlpha),
            BlendComponent(BlendFactor.One, BlendFactor.OneMinusSourceAlpha),
        )

        /**
         * Adds the source and destination values without attenuating either term.
         *
         * ```
         * resultColor = sourceColor + destinationColor
         *
         * resultAlpha = sourceAlpha + destinationAlpha
         * ```
         *
         * This preset is intended for accumulating quantities whose contributions should combine
         * additively, such as lighting, bloom, glow, emission, and other HDR effects.
         *
         * Source alpha does not weight the source color. If alpha represents the opacity or coverage of an
         * effect, the shader must apply that weighting before output:
         *
         * ```
         * weightedSourceColor = sourceColor * sourceAlpha
         * ```
         *
         * Alternatively, use a custom color equation with [BlendFactor.SourceAlpha] as the source factor.
         * A source with zero alpha but nonzero RGB still contributes color when this preset is used.
         *
         * Additive rendering commonly produces color values greater than one. Use a floating-point color
         * target when those values must remain available for later tone mapping or post-processing. An
         * unsigned-normalized target constrains the accumulated result and permanently loses intensity
         * above its representable range.
         *
         * This preset also adds alpha. Additive alpha represents an accumulated numeric quantity rather
         * than source-over coverage and may exceed one on a floating-point target. When only RGB should be
         * accumulated, exclude alpha through [ColorTargetState.writeMask]. When RGB should be additive but
         * alpha should track coverage, provide a separate source-over equation through
         * [BlendState.alpha].
         *
         * Full-strength addition is mathematically independent of draw order, but finite precision,
         * overflow, and intermediate clamping can still make differently ordered draws produce slightly
         * different stored results.
         */
        val Additive = BlendState(
            BlendComponent(BlendFactor.One, BlendFactor.One),
            BlendComponent(BlendFactor.One, BlendFactor.One)
        )
    }
}