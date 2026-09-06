/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.sampler

/**
 * Determines which mip levels contribute to a texture sample.
 *
 * Mip levels are successively smaller versions of an image. Sampling a smaller version when
 * the image occupies less screen space reduces the fine detail that would otherwise flicker
 * or form aliasing patterns as the view moves.
 *
 * A sample's level of detail selects a position in this resolution chain. This mode determines
 * whether sampling stays at the view's base level, selects one level, or blends adjacent levels.
 * [TextureFilter] separately determines how texels are filtered within each selected level.
 *
 * Levels are numbered relative to the texture view. These modes select existing levels;
 * generating their contents is a separate operation.
 */
enum class SamplerMipmapMode {
    /**
     * Uses only mip level zero of the texture view.
     *
     * This is useful when only the base image should contribute, such as an un-mipmapped
     * post-processing input. If a view starts at mip level two of the complete texture, its
     * level zero is that level, not the complete texture's full-resolution image.
     *
     * Disabling mip selection does not combine the minification and magnification filters:
     * the corresponding filter is still selected according to the sampling scale.
     */
    Disabled,

    /**
     * Selects the mip level nearest to the requested level of detail.
     *
     * For example, a level of detail of 2.3 selects level two when that level is available.
     * As the requested detail crosses the boundary between levels, sampling switches to the
     * other image rather than gradually blending the two.
     *
     * This is independent of nearest-texel filtering: the selected mip level can still use
     * [TextureFilter.Linear] to interpolate between its texels.
     */
    Nearest,

    /**
     * Blends samples from the two neighboring mip levels around the requested level of detail.
     *
     * For example, at a level of detail of 2.25, levels two and three contribute with weights
     * 0.75 and 0.25 when both are available. This smooths transitions as an image's apparent
     * scale changes. Selection remains within the levels exposed by the view.
     *
     * Combined with [TextureFilter.Linear], this gives ordinary two-dimensional sampling
     * bilinear filtering within each level and interpolation between levels, commonly called
     * trilinear filtering.
     */
    Linear,
}
