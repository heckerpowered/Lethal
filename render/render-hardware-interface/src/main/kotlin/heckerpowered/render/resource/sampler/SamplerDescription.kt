/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.sampler

/**
 * Describes how a texture sampler filters texels, selects mip levels, and handles image edges.
 *
 * A texture view selects the image data to sample. This description supplies the rules for
 * sampling that data, so the same image can be enlarged with sharp texel boundaries, filtered
 * smoothly, or repeated across a surface without changing its storage.
 *
 * Filtering and addressing are independent. For example, a repeating ground texture may use
 * linear filtering with repeat addressing, while a pixel-art sprite may use nearest filtering
 * with clamp-to-edge addressing.
 *
 * Horizontal and vertical refer to texture-space axes; they do not establish a screen-space
 * direction or a texture-origin convention. The description is an immutable value that can be
 * reused to create samplers or compared when selecting a cached sampler.
 */
data class SamplerDescription(
    /**
     * Filtering used when texture detail is reduced.
     *
     * A common case is a distant surface, where many texels of the original image cover a
     * small screen area. Mip selection can choose a lower-resolution level; this filter
     * determines how texels within each selected level contribute to the sample.
     */
    val minificationFilter: TextureFilter,

    /**
     * Filtering used when texture detail is enlarged.
     *
     * A common case is a nearby surface or an enlarged sprite, where individual texels cover
     * multiple screen pixels. Nearest filtering keeps distinct texel boundaries, while linear
     * filtering interpolates across them.
     */
    val magnificationFilter: TextureFilter,

    /** Addressing along the texture's U axis: horizontal for a two-dimensional image. */
    val addressModeU: SamplerAddressMode,

    /** Addressing along the texture's V axis: vertical for a two-dimensional image. */
    val addressModeV: SamplerAddressMode,

    /**
     * Addressing along the W axis of a three-dimensional texture's volume.
     *
     * W traverses volumetric depth, not array layers or cube faces. It is unused when sampling
     * an ordinary two-dimensional texture.
     */
    val addressModeW: SamplerAddressMode = SamplerAddressMode.ClampToEdge,

    /**
     * Selection or blending of mip levels, separate from filtering their individual texels.
     * [SamplerMipmapMode.Disabled] uses only the texture view's base level.
     */
    val mipmapMode: SamplerMipmapMode = SamplerMipmapMode.Disabled,
)
