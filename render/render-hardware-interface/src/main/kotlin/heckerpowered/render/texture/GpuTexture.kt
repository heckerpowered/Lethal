/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

import heckerpowered.render.GpuResource
import heckerpowered.render.pipeline.multisample.SampleCount

/**
 * Stores a one-dimensional sequence, a two-dimensional image, or a three-dimensional volume
 * of texels, together with its mip levels and array layers.
 *
 * A texture represents the complete image storage. Rendering and shader access use
 * [GpuTextureView]s that select a subresource range from this texture.
 *
 * [dimension] describes the spatial grid. Independent images are counted by [arrayLayerCount],
 * not by [depth]; a volume instead has a third spatial axis and one array layer.
 *
 * [storage] determines whether image data can be kept for later use or is temporary working
 * data for rendering. Selecting a view does not change the storage mode of its subresources.
 *
 * This abstraction intentionally uses the cross-backend term "texture" rather than the
 * Vulkan-specific term "image". Depending on the backend, it may be implemented by a Vulkan
 * image, an OpenGL texture object, a Metal texture, or an equivalent native resource.
 *
 * The name does not imply that the resource is necessarily shader-sampleable. Its permitted
 * uses, such as sampling, rendering, storage access, and copying, are determined by [usage].
 */
interface GpuTexture : GpuResource {
    /**
     * Spatial structure of the complete texture.
     *
     * This is fixed at creation or import. A size of one along an axis does not change the
     * dimension, and selecting an array layer does not turn that layer into a spatial axis.
     */
    val dimension: TextureDimension

    /**
     * Storage mode of the complete texture.
     *
     * The mode is fixed when this texture is created or imported. Selecting a view or discarding
     * image contents does not change it.
     */
    val storage: TextureStorage

    /**
     * Uses enabled for this texture at creation or import.
     *
     * An operation requiring a use absent from this set must be rejected. See [TextureUsage]
     * for why these requirements are established before creating storage.
     *
     * The set remains unchanged and must be exposed as an unmodifiable set. Imported textures
     * expose only uses supported by their existing storage and permitted by the import contract.
     */
    val usage: Set<TextureUsage>

    /**
     * Number of samples represented at each pixel location in this texture.
     *
     * [SampleCount.One] is an ordinary single-sampled image. With [SampleCount.Four], a pixel
     * location holds four color, depth, or stencil samples, according to [format]. Width and
     * height still describe the image grid; four samples do not make either dimension four
     * times larger.
     *
     * This is a property of the storage, independent of which pipeline is currently bound.
     * A texture view preserves the count. Obtaining a single-sampled result from multisampled
     * data requires a resolve operation into a separate destination rather than another view.
     *
     * Multisampled textures are two-dimensional and have only mip level zero. Supported counts
     * depend on the format, usage, storage mode, and graphics device.
     */
    val sampleCount: SampleCount

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
     * Height in texels of mip level zero. One-dimensional textures report one.
     *
     * Lower mip levels have successively reduced dimensions. The height of mip level `level` is
     * `max(1, height >> level)`.
     *
     * This is the base height of the complete texture, not necessarily the height exposed by a
     * particular [GpuTextureView].
     */
    val height: Int

    /**
     * Size in texels of the third spatial axis at mip level zero.
     *
     * For a volume, this is the number of positions from front to back, not a depth-test value
     * or an array-layer count. A 64-by-64-by-32 density field has a depth of 32 and one array
     * layer. One- and two-dimensional textures report one.
     *
     * Lower mip levels reduce this spatial size along with width and height. The depth at
     * mip level `level` is `max(1, depth >> level)`.
     */
    val depth: Int

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
     * dimensions. A multisampled texture must have a count of `1`.
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
     * This count does not decrease at lower mip levels. A three-dimensional texture has one
     * array layer; its depth slices are positions within that layer rather than separate layers.
     * Cube views use suitable groups of six layers when [cubeCompatible] is `true`.
     */
    val arrayLayerCount: Int

    /**
     * Whether this texture permits cube views of suitable groups of six array layers.
     *
     * Use this when selecting an existing texture for cube access without copying its contents
     * into another allocation. The value describes compatibility established by the device,
     * not merely the value supplied in a creation request. Imported textures report only the
     * compatibility supported by their representation and admitted by the import contract.
     *
     * A value of `true` requires single-sampled, square two-dimensional storage with at least
     * six array layers. Those dimensions alone are not sufficient: the backend must also have
     * established or recognized a compatible representation. A value of `false` means cube
     * views are not available for this texture through the device.
     *
     * This property remains unchanged. It establishes storage compatibility, not the validity
     * of every view request. The view format, selected mip and layer ranges, and cube-array
     * support are checked separately; accesses must still be permitted by [usage].
     */
    val cubeCompatible: Boolean
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

/**
 * Returns the size of the third spatial axis in texels at the specified mip level.
 *
 * One- and two-dimensional textures return one. For volumes this shrinks with the mip level;
 * it is not the array-layer count.
 *
 * @throws IllegalArgumentException if [level] is outside `[0, mipLevelCount)`.
 */
fun GpuTexture.depthAtMipLevel(level: Int): Int {
    require(level in 0 until mipLevelCount) { "Mip level $level is outside [0, $mipLevelCount)" }
    return (depth shr level).coerceAtLeast(1)
}
