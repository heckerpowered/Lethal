/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

import heckerpowered.render.TextureFormat

/**
 * A view exposing a selected subresource range of a [GpuTexture].
 *
 * A texture view does not contain independent image storage. It references [texture] and
 * restricts access to consecutive mip levels and array layers of that texture.
 *
 * Rendering and shader access normally operate on texture views rather than complete textures.
 * Different views may expose different subresource ranges of the same underlying texture without
 * copying its contents.
 *
 * Mip levels and array layers are renumbered relative to the view. Mip level zero and array layer
 * zero through this view refer to [baseMipLevel] and [baseArrayLayer] of [texture], respectively.
 *
 * The lifetime of this view must not exceed the lifetime of [texture].
 */
interface GpuTextureView {
    /**
     * Complete texture storage exposed through this view.
     *
     * The view references the texture's existing storage and does not contain an independent copy
     * of its texels.
     */
    val texture: GpuTexture

    /**
     * Index of the first mip level of [texture] exposed through this view.
     *
     * Mip level zero is the full-resolution level of the underlying texture. Mip level zero as
     * observed through this view refers to this mip level.
     *
     * For example, if this property is `2`, accessing mip level zero through the view accesses mip
     * level two of [texture].
     */
    val baseMipLevel: Int

    /**
     * Number of consecutive mip levels exposed through this view.
     *
     * The selected range is
     * `[baseMipLevel, baseMipLevel + mipLevelCount)`.
     *
     * A value of `1` exposes only [baseMipLevel]. A render attachment normally exposes exactly one
     * mip level, while a sampled view may expose multiple levels for mipmapped sampling.
     */
    val mipLevelCount: Int

    /**
     * Index of the first array layer of [texture] exposed through this view.
     *
     * Array layer zero as observed through this view refers to this layer of the underlying
     * texture.
     *
     * Ordinary non-array two-dimensional textures contain one layer at index zero.
     */
    val baseArrayLayer: Int

    /**
     * Number of consecutive array layers exposed through this view.
     *
     * The selected range is
     * `[baseArrayLayer, baseArrayLayer + arrayLayerCount)`.
     *
     * A value of `1` exposes only [baseArrayLayer]. Views spanning multiple layers may be used for
     * texture arrays, cube textures, or layered rendering.
     */
    val arrayLayerCount: Int

    /**
     * Width in texels of mip level zero as observed through this view.
     *
     * This is the width of [baseMipLevel] in [texture], not necessarily the base width of the
     * complete texture.
     */
    val width: Int
        get() = texture.widthAtMipLevel(baseMipLevel)

    /**
     * Height in texels of mip level zero as observed through this view.
     *
     * This is the height of [baseMipLevel] in [texture], not necessarily the base height of the
     * complete texture.
     */
    val height: Int
        get() = texture.heightAtMipLevel(baseMipLevel)

    /**
     * Format through which this view exposes its subresources.
     *
     * This currently matches the storage format of [texture]. If compatible format
     * reinterpretation is introduced later, a view may instead expose an explicitly selected
     * compatible format.
     */
    val format: TextureFormat
        get() = texture.format
}