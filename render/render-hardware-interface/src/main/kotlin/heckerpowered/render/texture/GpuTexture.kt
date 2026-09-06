/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

import heckerpowered.render.GpuResource

/**
 * GPU-resident texture storage containing one or more mip levels and array layers.
 *
 * A texture represents the complete allocated storage. Rendering and shader access use
 * [GpuTextureView]s that select a subresource range from this texture.
 *
 * This abstraction intentionally uses the cross-backend term "texture" rather than the
 * Vulkan-specific term "image". Depending on the backend, it may be implemented by a Vulkan
 * image, an OpenGL texture object, a Metal texture, or an equivalent native resource.
 *
 * The name does not imply that the resource is necessarily shader-sampleable. Its permitted
 * uses, such as sampling, rendering, storage access, and copying, are determined by its usage.
 */
interface GpuTexture : GpuResource {
    /**
     * Width in texels of mip level zero.
     *
     * Lower mip levels have successively reduced dimensions. The width of mip level `level` is
     * `max(1, width >> level)`.
     *
     * This is the base width of the complete texture, not necessarily the width exposed by a
     * particular [GpuTextureView].
     */
    val width: Int

    /**
     * Height in texels of mip level zero.
     *
     * Lower mip levels have successively reduced dimensions. The height of mip level `level` is
     * `max(1, height >> level)`.
     *
     * This is the base height of the complete texture, not necessarily the height exposed by a
     * particular [GpuTextureView].
     */
    val height: Int

    /**
     * Storage format of the texture.
     *
     * The format defines the channel layout, numeric representation, and, where applicable,
     * color encoding of every subresource in this texture.
     *
     * A format being represented by [TextureFormat] does not guarantee that the current device
     * supports every possible use of it. Sampling, filtering, blending, render attachment use,
     * and storage access may have additional backend-specific capability requirements.
     */
    val format: TextureFormat

    /**
     * Total number of mip levels allocated for this texture, including mip level zero.
     *
     * A value of `1` means that only the full-resolution level exists. For example, a value of
     * `4` exposes mip levels `0`, `1`, `2`, and `3`.
     *
     * The count must not exceed the complete mip chain permitted by the texture's base
     * dimensions.
     */
    val mipLevelCount: Int

    /**
     * Total number of array layers allocated for each mip level.
     *
     * An ordinary non-array two-dimensional texture has one array layer at index zero. Texture
     * arrays contain multiple equally formatted and equally sized images distinguished by their
     * layer index.
     *
     * Array layers are independent from color attachment indices: layers represent multiple
     * instances of the same kind of image, while multiple color attachments represent different
     * outputs of the same render pass.
     *
     * Cube textures are commonly represented by groups of six array layers, although their
     * interpretation also depends on the texture or view dimension.
     */
    val arrayLayerCount: Int
}

/**
 * Returns the height in texels of the specified mip level.
 *
 * @throws IllegalArgumentException if [level] is outside `[0, mipLevelCount)`.
 */
fun GpuTexture.heightAtMipLevel(level: Int): Int {
    require(level in 0 until mipLevelCount) { "Mip level $level is outside [0, $mipLevelCount)" }
    return (height shr level).coerceAtLeast(1)
}

/**
 * Returns the width in texels of the specified mip level.
 *
 * @throws IllegalArgumentException if [level] is outside `[0, mipLevelCount)`.
 */
fun GpuTexture.widthAtMipLevel(level: Int): Int {
    require(level in 0 until mipLevelCount) { "Mip level $level is outside [0, $mipLevelCount)" }
    return (width shr level).coerceAtLeast(1)
}