/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

/**
 * Exposes selected texture data through a particular image, array, cube, or volume interface.
 *
 * A material array can provide one view for array sampling and another view selecting a single
 * picture for attachment access. The views refer to the same texels; they are not copies and
 * do not need to be updated after writes to the underlying image.
 *
 * [dimension] supplies the access form, while [texture]'s dimension describes storage. [aspects]
 * selects color, depth, or stencil data. The mip and layer ranges select existing subresources
 * and remain fixed for the view's lifetime.
 *
 * Mip zero through this view refers to [baseMipLevel] of [texture]. For array views, array index
 * zero refers to [baseArrayLayer]. Cube arrays instead group faces in sets of six; see
 * [TextureViewDimension.CubeArray]. A volume view includes the complete depth at each selected
 * mip, using base array layer zero and count one rather than treating z slices as array layers.
 *
 * [texture] remains the source of storage mode, sample count, and permitted usages. A view
 * neither resolves samples nor enables a missing usage. In particular, another view cannot
 * preserve memoryless contents beyond the scope in which the storage makes them available.
 *
 * The format remains [texture]'s format; [aspects] can select depth from a combined depth-stencil
 * format without changing this property to a different format. A consumer must consider both
 * the format and the aspect selection, as well as its required usage and view dimension.
 *
 * View creation establishes access to storage, not initialized image contents or synchronization.
 * Views become unusable when their source texture is no longer valid. Backend state established
 * for a device-created view is managed with the source texture, not through a separate view close
 * operation; see [heckerpowered.render.GraphicsDevice.createTextureView].
 */
interface GpuTextureView {
    /**
     * Interface through which the selected images are addressed.
     *
     * This need not equal the storage dimension: six layers of two-dimensional storage can form
     * a Cube view. It is not inferred from the selected layer count.
     */
    val dimension: TextureViewDimension

    /**
     * Parts of the texture's data exposed through this view.
     *
     * For a combined depth-stencil texture, a depth-only view exposes the depth image, a
     * stencil-only view exposes its integer marks, and a combined view exposes both parts.
     * These views may select exactly the same mip levels and layers; the aspect selection
     * distinguishes which data at those positions is exposed. See [TextureAspect].
     *
     * Implementations expose a stable, unmodifiable set. This selection does not enable depth
     * or stencil testing; the pipeline and the consuming operation determine how the data is used.
     */
    val aspects: Set<TextureAspect>

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
     * Ordinary non-array two-dimensional textures contain one layer at index zero. Views of
     * three-dimensional textures also use zero here; this property is not a depth-slice offset.
     */
    val baseArrayLayer: Int

    /**
     * Number of consecutive array layers exposed through this view.
     *
     * The selected range is
     * `[baseArrayLayer, baseArrayLayer + arrayLayerCount)`.
     *
     * Cube views count faces here: Cube selects six, and CubeArray selects a positive multiple
     * of six. The number of cube-array elements is therefore `arrayLayerCount / 6`. Ordinary
     * array views count independent images. Three-dimensional views use one; their spatial
     * depth is measured by [depth] rather than by this count.
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
     * Third-axis size in texels of mip level zero as observed through this view.
     *
     * This is the depth of [baseMipLevel] in [texture], which may be smaller than the complete
     * texture's base depth. It is one for one- and two-dimensional textures.
     */
    val depth: Int
        get() = texture.depthAtMipLevel(baseMipLevel)

    /**
     * Format through which this view exposes its subresources.
     *
     * This matches the storage format of [texture]. A Depth-only view of combined depth-stencil
     * storage still reports the combined format; [aspects] determines which part it exposes.
     * Creating a view does not reinterpret or convert the stored representation.
     */
    val format: TextureFormat
        get() = texture.format
}
