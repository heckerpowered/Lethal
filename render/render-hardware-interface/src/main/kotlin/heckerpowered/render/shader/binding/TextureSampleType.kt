/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.binding

import heckerpowered.render.resource.texture.TextureAspect
import heckerpowered.render.resource.texture.TextureFormat

/**
 * Describes the numeric values a shader receives from formatted image or texel-buffer reads.
 *
 * Storage formats and shader values are different concepts. An 8-bit normalized color and a
 * 16-bit floating-point color can both be read as floating-point values, while an integer
 * identifier must be read through an integer interface. This lets a binding describe the
 * required numeric category without fixing one particular sampled image format.
 *
 * The selected aspect matters: depth is read as floating point, while stencil is read as an
 * unsigned integer. A combined depth-stencil format therefore cannot determine this category
 * without knowing which aspect is exposed. Filtering and comparison sampling require separate
 * compatibility checks; choosing Float does not guarantee that either operation is supported.
 */
enum class TextureSampleType {
    /** Floating-point results, including normalized colors and depth values. */
    Float,

    /** Signed integer results, without normalization into a floating-point range. */
    SignedInteger,

    /** Unsigned integer results, such as stencil marks and unsigned identifiers. */
    UnsignedInteger;

    companion object {
        /**
         * Identifies the numeric values exposed by one aspect of a formatted resource.
         *
         * Normalized color formats are read as floating-point values even though their stored
         * components are integers. Stencil preserves integer marks instead: selecting Depth
         * from a combined depth-stencil format produces [Float], while selecting Stencil from
         * that same format produces [UnsignedInteger].
         *
         * This classifies the values; it neither converts them nor proves that sampling,
         * filtering, storage access, or a particular descriptor is supported by a device.
         *
         * @throws IllegalArgumentException if [format] does not contain [aspect].
         */
        fun from(format: TextureFormat, aspect: TextureAspect): TextureSampleType = when (aspect) {
            TextureAspect.Color -> colorSampleType(format)
            TextureAspect.Depth -> {
                require(format.hasDepth) { "Format $format has no depth aspect" }
                Float
            }

            TextureAspect.Stencil -> {
                require(format.hasStencil) { "Format $format has no stencil aspect" }
                UnsignedInteger
            }
        }
    }
}

// Keep this exhaustive: adding a format must also decide its shader-visible numeric category.
private fun colorSampleType(format: TextureFormat): TextureSampleType = when (format) {
    TextureFormat.R8UnsignedNormalized,
    TextureFormat.Rg8UnsignedNormalized,
    TextureFormat.Rgba8UnsignedNormalized,
    TextureFormat.Rgba8UnsignedNormalizedSrgb,
    TextureFormat.Bgra8UnsignedNormalized,
    TextureFormat.Bgra8UnsignedNormalizedSrgb,
    TextureFormat.R16Float,
    TextureFormat.Rg16Float,
    TextureFormat.Rgba16Float,
    TextureFormat.R32Float,
    TextureFormat.Rg32Float,
    TextureFormat.Rgba32Float,
        -> TextureSampleType.Float

    TextureFormat.Depth24UnsignedNormalized,
    TextureFormat.Depth32Float,
    TextureFormat.Depth24UnsignedNormalizedStencil8,
    TextureFormat.Depth32FloatStencil8UnsignedInteger,
        ->
        throw IllegalArgumentException("Format $format has no color aspect")
}
