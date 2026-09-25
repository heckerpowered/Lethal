/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.resource.texture

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.pipeline.multisample.SampleCount
import java.util.*

/**
 * Describes the image storage to be created by a graphics device.
 *
 * Ordinary images need a width, height, format, and the operations that will use them. The
 * defaults describe a single-sampled, two-dimensional texture with one mip level, one array
 * layer, and backing storage. For example, an uploaded material image requests
 * [TextureUsage.TransferDestination] and [TextureUsage.Sampled].
 *
 * The same description can request a one-dimensional lookup, an array of independent pictures,
 * or a three-dimensional volume. [dimension] selects the spatial structure; [arrayLayerCount]
 * is separate from [depth]. The usage set is copied, so later changes to the supplied collection
 * cannot change the request.
 *
 * This describes storage, not initial image contents. Allocating mip levels reserves those
 * levels without generating their data. Uploads, clears, drawing, and resolve operations
 * establish contents separately; creation makes no zero-initialization guarantee.
 *
 * Construction checks shape and role consistency without consulting a device. A valid description
 * is not a promise of hardware support: [GraphicsDevice.createTexture] checks the requested
 * combination of format, dimensions, sample count, storage mode, and uses.
 *
 * @throws IllegalArgumentException if sizes or counts are invalid, the shape cannot represent
 * the requested dimension or cube compatibility, or a usage contradicts the format or sample count.
 */
class TextureDescription(
    val label: String,

    /** Number of texels along the first axis at mip level zero. */
    val width: Int,

    /**
     * Number of texels along the second axis at mip level zero. One-dimensional textures use one.
     */
    val height: Int = 1,

    /**
     * Number of texels along the third axis at mip level zero.
     *
     * Only a three-dimensional texture uses this as a spatial extent. One- and two-dimensional
     * textures use one; their independent images are counted by [arrayLayerCount] instead.
     */
    val depth: Int = 1,

    val format: TextureFormat,
    usage: Set<TextureUsage>,
    val dimension: TextureDimension = TextureDimension.TwoDimensional,

    /**
     * Number of mip levels to allocate, including the full-resolution level zero.
     *
     * Each next level halves every spatial size with integer rounding down, stopping at one.
     * Array layers do not shrink. For example, a 64-by-64-by-32 volume may allocate up to seven
     * levels, ending at 1-by-1-by-1. A shorter chain is allowed; multisampled textures use one level.
     */
    val mipLevelCount: Int = 1,

    /**
     * Number of independent images at each mip level.
     *
     * A two-dimensional material array might contain sixteen images of equal dimensions and
     * format. A three-dimensional volume instead has one array layer and uses [depth] for its
     * third axis. This count stays the same at every mip level.
     */
    val arrayLayerCount: Int = 1,

    /**
     * Samples stored at each texel location. Multisampling uses two-dimensional storage with
     * one mip level; it does not enlarge the spatial dimensions or create a resolve target.
     */
    val sampleCount: SampleCount = SampleCount.One,

    /**
     * [TextureStorage.Backed] is the ordinary choice for retaining data for later use.
     * [TextureStorage.Memoryless] requests temporary attachment storage and does not permit
     * silently falling back to a different storage mode.
     */
    val storage: TextureStorage = TextureStorage.Backed,

    /**
     * Requires storage that supports cube views of suitable groups of six array layers.
     *
     * A cube view uses a direction to select one of six two-dimensional faces and a position
     * within that face. This is useful for environment images queried by viewing or reflection
     * direction. The storage must be prepared for this access when it is created; allocating
     * six square layers alone does not establish that compatibility.
     *
     * A value of `true` makes cube compatibility a creation requirement. Successful creation
     * must return a texture whose [GpuTexture.cubeCompatible] is `true`, or creation must fail.
     * The default `false` makes no such requirement; it does not require the device to disable
     * compatibility that it can otherwise establish and expose.
     *
     * This requires single-sampled, square two-dimensional storage with at least six layers.
     * The complete allocation need not contain a multiple of six layers. The selected mip
     * and layer ranges, view format, and cube-array support are checked when a view is created.
     * This option does not create a view or add roles to [usage].
     */
    val cubeCompatible: Boolean = false,
) {
    /**
     * A stable, unmodifiable snapshot of the requested operations.
     *
     * Backend implementation flags may differ, but they do not grant extra RHI access roles.
     */
    val usage: Set<TextureUsage> = Collections.unmodifiableSet(usage.toSet())

    init {
        require(width > 0 && height > 0 && depth > 0) { "Texture dimensions must be positive" }
        require(arrayLayerCount > 0) { "Texture array layer count must be positive" }
        require(mipLevelCount > 0) { "Texture mip level count must be positive" }
        require(this.usage.isNotEmpty()) { "Texture requires at least one usage" }

        when (dimension) {
            TextureDimension.OneDimensional -> require(height == 1 && depth == 1) { "One-dimensional textures require height and depth to be one" }
            TextureDimension.TwoDimensional -> require(depth == 1) { "Two-dimensional textures require depth to be one" }
            TextureDimension.ThreeDimensional -> require(arrayLayerCount == 1) { "Three-dimensional textures use depth rather than array layers" }
        }

        val largestDimension = maxOf(width, height, depth)
        val maximumMipLevelCount = Int.SIZE_BITS - largestDimension.countLeadingZeroBits()
        require(mipLevelCount <= maximumMipLevelCount) { "Texture mip level count exceeds the complete chain of $maximumMipLevelCount levels" }

        if (sampleCount != SampleCount.One) {
            require(dimension == TextureDimension.TwoDimensional) { "Multisampled textures must be two-dimensional" }
            require(mipLevelCount == 1) { "Multisampled textures require exactly one mip level" }
        }

        if (cubeCompatible) {
            require(dimension == TextureDimension.TwoDimensional) { "Cube-compatible storage must be two-dimensional" }
            require(width == height && arrayLayerCount >= 6) { "Cube-compatible storage requires square images and at least six array layers" }
            require(sampleCount == SampleCount.One) { "Cube-compatible storage must be single-sampled" }
        }

        when {
            TextureUsage.ColorAttachment in this.usage -> require(format.isColor) { "Color attachment usage requires a color format" }
            TextureUsage.DepthStencilAttachment in this.usage -> require(format.hasDepth || format.hasStencil) { "Depth-stencil attachment usage requires a depth or stencil aspect" }
            TextureUsage.ResolveSource in this.usage -> require(sampleCount != SampleCount.One) { "Resolve source usage requires multiple samples" }
            TextureUsage.ResolveDestination in this.usage -> require(sampleCount == SampleCount.One) { "Resolve destination usage requires one sample" }
        }
    }
}
