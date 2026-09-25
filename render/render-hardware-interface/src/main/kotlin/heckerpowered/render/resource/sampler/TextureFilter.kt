/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.sampler

/**
 * Determines how a sampled value is obtained from the texels of one mip level.
 *
 * A texture stores values on a discrete grid, but sampling coordinates can fall between texel
 * centers. Filtering determines whether sampling selects a nearby texel or interpolates between
 * neighboring texels to represent the value at that position.
 *
 * A sampler selects filters separately for minification and magnification, allowing a texture
 * to behave differently when its detail is reduced or enlarged.
 *
 * Filtering within a mip level is separate from selecting or blending mip levels.
 * [SamplerMipmapMode] controls that part of sampling.
 */
enum class TextureFilter {
    /**
     * Selects the texel nearest to the sampling position without blending neighboring texels
     * within the selected mip level.
     *
     * As the sampling position moves between two texel centers, the selected value changes when
     * the other texel becomes nearer. It does not pass gradually through intermediate values.
     *
     * When an image is magnified, this keeps individual texel boundaries visible. It is useful
     * for pixel art and for data whose neighboring values should remain distinct rather than
     * being interpolated.
     */
    Nearest,

    /**
     * Interpolates between neighboring texels according to the sampling position.
     *
     * For a two-dimensional texture, ordinary linear filtering combines four neighboring texels,
     * interpolating along both the horizontal and vertical axes. This is bilinear filtering.
     * The contributions depend on position rather than always being an equal average.
     *
     * For example, consider two adjacent floating-point texels containing 0.0 and 1.0. Along the
     * line between their centers, the ideal interpolated value is 0.25 at one quarter of the
     * distance from the first center to the second, and 0.5 at the midpoint.
     *
     * This gives magnified images smoother transitions, but can soften boundaries that were
     * intended to remain sharp. It does not replace mipmapping when many texels contribute to
     * a much smaller image.
     *
     * The texture view's format must support linear filtering.
     */
    Linear,
}
