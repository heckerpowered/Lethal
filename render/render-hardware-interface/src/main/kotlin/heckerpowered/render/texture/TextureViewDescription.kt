/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.pipeline.multisample.SampleCount
import java.util.*

/**
 * Selects the part of an existing texture that a view will expose and how users will address it.
 *
 * A view can expose one picture from an array, several mip levels for sampling, or six faces
 * as a cube. The description selects existing data; it does not allocate an image, generate
 * missing mip contents, or change the texture's format, sample count, storage, or usage.
 *
 * The defaults select the color aspect of mip zero and layer zero as an ordinary two-dimensional
 * image. Counts are literal, not shorthand for the remaining range. A cube, a depth view, or a
 * longer mip chain must be requested explicitly rather than inferred from the source texture.
 *
 * All base indices refer to the complete source texture, not to a previously created view.
 * For example, base mip 2 with count 3 selects texture mips 2, 3, and 4, which are exposed as
 * view mips 0, 1, and 2. Array layers are similarly relative to the selected base; cube-array
 * indexing groups the selected faces as described by [TextureViewDimension.CubeArray].
 *
 * Construction checks the request's own consistency. [validateFor] checks its relationship
 * to a texture's metadata. [GraphicsDevice.createTextureView] additionally establishes device
 * support and resource validity before returning a usable view.
 *
 * @throws IllegalArgumentException if a base is negative, a count is not positive, or the
 * requested layer or aspect selection contradicts the view dimension or aspect model.
 */
class TextureViewDescription(
    val label: String = "",
    val dimension: TextureViewDimension = TextureViewDimension.TwoDimensional,
    aspects: Set<TextureAspect> = setOf(TextureAspect.Color),

    /** First mip level in the complete texture. It becomes mip level zero through the view. */
    val baseMipLevel: Int = 0,

    /** Number of consecutive mip levels to expose. A count of one selects only [baseMipLevel]. */
    val mipLevelCount: Int = 1,

    /**
     * First layer in the complete texture, or first face for cube views.
     *
     * This does not count cubes and does not select volume slices. Three-dimensional views use
     * zero. Cube face grouping starts here, not at a hidden or rounded-down layer boundary.
     */
    val baseArrayLayer: Int = 0,

    /**
     * Number of consecutive layers, counting individual faces for cube views.
     *
     * Non-array one- and two-dimensional views expose one layer; Cube exposes six; CubeArray
     * exposes a positive multiple of six. Three-dimensional views use one and include the
     * complete depth of each selected mip. This count does not change with the mip level.
     */
    val arrayLayerCount: Int = 1,
) {
    /**
     * Selects the image data to expose at the requested mip levels and array layers.
     *
     * For example, `{Depth}` requests only the depth image from combined depth-stencil storage;
     * `{Depth, Stencil}` requests both parts for a use that accepts them together. The source
     * format must contain every selected aspect. See [TextureAspect] for why a combined texture
     * can need different selections.
     *
     * The default selects Color alone. The other supported selections are Depth alone, Stencil
     * alone, and Depth with Stencil. The set is copied into an unmodifiable snapshot so later
     * changes to the caller's collection cannot change the request.
     */
    val aspects: Set<TextureAspect> = Collections.unmodifiableSet(aspects.toSet())

    init {
        require(baseMipLevel >= 0) { "Texture view base mip level must not be negative" }
        require(mipLevelCount > 0) { "Texture view mip level count must be positive" }
        require(baseArrayLayer >= 0) { "Texture view base array layer must not be negative" }
        require(arrayLayerCount > 0) { "Texture view array layer count must be positive" }
        require(this.aspects.isNotEmpty()) { "Texture view requires at least one aspect" }
        require(TextureAspect.Color !in this.aspects || this.aspects.size == 1) {
            "Color cannot be combined with depth or stencil in a texture view"
        }

        when (dimension) {
            TextureViewDimension.OneDimensional,
            TextureViewDimension.TwoDimensional,
                -> require(arrayLayerCount == 1) {
                "Non-array texture views select exactly one array layer"
            }

            TextureViewDimension.Cube -> require(arrayLayerCount == 6) {
                "A cube view selects exactly six array layers"
            }

            TextureViewDimension.CubeArray -> require(arrayLayerCount % 6 == 0) {
                "A cube-array view selects a multiple of six array layers"
            }

            TextureViewDimension.ThreeDimensional -> require(baseArrayLayer == 0 && arrayLayerCount == 1) {
                "Three-dimensional views include the volume rather than an array-layer slice"
            }

            TextureViewDimension.OneDimensionalArray,
            TextureViewDimension.TwoDimensionalArray,
                -> Unit
        }
    }

    /**
     * Checks this request against the source texture's reported shape, aspects, and sample count.
     *
     * The selected ranges must fit exactly; oversized requests are rejected rather than clamped.
     * Cube access requires the storage's declared cube compatibility, not just six square layers.
     *
     * Backends can reuse these checks before establishing a view. This does not check device
     * identity, whether the resource is still valid, native view support, or compatibility with a
     * particular binding or render pass. It never reads image contents or creates backend state.
     *
     * @throws IllegalArgumentException if the ranges, dimension, aspects, or multisample selection
     * are incompatible with [texture].
     */
    fun validateFor(texture: GpuTexture) {
        require(baseMipLevel < texture.mipLevelCount) {
            "Texture view base mip level $baseMipLevel is outside ${texture.mipLevelCount} levels"
        }
        // Subtract only after validating the base, so an invalid end index cannot overflow.
        require(mipLevelCount <= texture.mipLevelCount - baseMipLevel) {
            "Texture view mip range exceeds the source texture"
        }
        require(baseArrayLayer < texture.arrayLayerCount) {
            "Texture view base array layer $baseArrayLayer is outside ${texture.arrayLayerCount} layers"
        }
        require(arrayLayerCount <= texture.arrayLayerCount - baseArrayLayer) {
            "Texture view array layer range exceeds the source texture"
        }

        val requiredDimension = when (dimension) {
            TextureViewDimension.OneDimensional,
            TextureViewDimension.OneDimensionalArray,
                -> TextureDimension.OneDimensional

            TextureViewDimension.TwoDimensional,
            TextureViewDimension.TwoDimensionalArray,
            TextureViewDimension.Cube,
            TextureViewDimension.CubeArray,
                -> TextureDimension.TwoDimensional

            TextureViewDimension.ThreeDimensional -> TextureDimension.ThreeDimensional
        }
        require(texture.dimension == requiredDimension) {
            "View dimension $dimension requires $requiredDimension storage, not ${texture.dimension}"
        }

        for (aspect in aspects) {
            val present = when (aspect) {
                TextureAspect.Color -> texture.format.isColor
                TextureAspect.Depth -> texture.format.hasDepth
                TextureAspect.Stencil -> texture.format.hasStencil
            }
            require(present) { "Texture format ${texture.format} does not contain the $aspect aspect" }
        }

        if (dimension == TextureViewDimension.Cube || dimension == TextureViewDimension.CubeArray) {
            require(texture.cubeCompatible) { "The source texture does not permit cube views" }
            require(texture.width == texture.height) { "Cube views require square source images" }
            require(texture.sampleCount == SampleCount.One) { "Cube views require single-sampled storage" }
        }

        if (texture.sampleCount != SampleCount.One) {
            require(
                dimension == TextureViewDimension.TwoDimensional ||
                        dimension == TextureViewDimension.TwoDimensionalArray
            ) {
                "Multisampled storage requires a two-dimensional or two-dimensional-array view"
            }
            require(baseMipLevel == 0 && mipLevelCount == 1) {
                "Multisampled views select only mip level zero"
            }
        }
    }
}
