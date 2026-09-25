/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command

import heckerpowered.render.memory.Size
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.texture.TextureAspect
import heckerpowered.render.resource.texture.TextureFormat

/**
 * Describes how rows of texture data are arranged in host memory or a GPU buffer.
 *
 * A three-pixel RGBA8 row occupies twelve bytes, but a producer may place successive rows
 * sixteen bytes apart. The four intervening bytes are padding, not an extra pixel. This layout
 * lets an upload or readback address the rows without pretending that the image is wider.
 * [ImageRegion] independently selects where those texels belong in the image.
 *
 * The first texel starts at the supplied host address or at byte zero of the supplied buffer
 * view. Within a row, texels are contiguous in increasing x order; rows advance in increasing y
 * order using the region's upper-left convention. There is no additional source offset here:
 * adjust the address or select a buffer subview instead. Negative strides are not represented.
 *
 * A slice is one selected two-dimensional plane. For a volume, successive slices advance z;
 * for an array, they advance the selected array layer (including cube faces). These are different
 * image axes, not interchangeable selections: a volume has one array layer, and an array image
 * has depth one. Slice numbering starts at zero within the operation, irrespective of the
 * region's absolute z or layer offset.
 *
 * This describes linear transfer data, not the texture's internal tiling, allocation size,
 * Vulkan image-layout state, or shader member layout. Creating it does not allocate memory or
 * perform format conversion. [footprintFor] combines the strides with one region's extent and
 * transfer encoding so callers can reserve enough linear storage.
 *
 * ### Texel encoding
 *
 * The region's format and selected aspect determine each transferred texel; the two endpoints
 * cannot specify unrelated formats. Multi-byte components and packed words use native host
 * byte order. Color components follow the format's storage order (R, RG, RGBA, or BGRA), with
 * no padding between components. Half and full floats use IEEE 754 binary16 and binary32.
 * sRGB formats transfer their stored encoded RGB bytes, not decoded linear colors; alpha remains
 * unsigned-normalized. This is not a color-space conversion interface.
 *
 * Depth and stencil use separate linear representations, even in combined storage:
 *
 * | Selected data | Representation of one texel |
 * | --- | --- |
 * | 24-bit normalized depth | Four-byte word; bits 0..23 contain the unsigned depth code |
 * | 32-bit floating depth | One four-byte binary32 value |
 * | Stencil | One unsigned byte |
 *
 * For 24-bit depth, bits 24..31 are ignored on upload and unspecified on readback. They are
 * part of the transferred word, not the stencil value or row padding. This interface chooses
 * a separate depth word rather than an interleaved depth/stencil word. Depth transfers use
 * finite values in [0, 1] so they do not require unrestricted native depth-range support;
 * metadata checks do not scan the payload.
 * Uploading one aspect must preserve the other aspect. A backend must translate its native
 * transfer representation when necessary, or report that it cannot perform the operation.
 *
 * Only the texel words in each occupied row are accessed. Row gaps, slice gaps, and bytes after
 * the last row need not be initialized for an upload and remain unchanged in a destination
 * buffer. Unused bits inside a texel word have the separate rule above. The current formats
 * are uncompressed; this layout does not implicitly invent a block layout for future formats.
 *
 * @throws IllegalArgumentException if either stride is negative.
 * @see <a href="https://docs.vulkan.org/spec/latest/chapters/copies.html#copies-buffers-images-addressing">Vulkan buffer/image addressing and aspect encodings</a>
 */
data class TextureDataLayout(
    /**
     * Byte distance between row starts. Zero selects `width * texelSizeBytes` for the region.
     * An explicit stride must contain a complete row, so rows never overlap.
     */
    val rowStrideBytes: Size = 0,
    /**
     * Byte distance between slice starts. Zero selects `effectiveRowStride * height`.
     * An explicit stride must cover the last occupied byte of a slice. It need not be a
     * multiple of the row stride; native execution may need a different transfer path.
     */
    val sliceStrideBytes: Size = 0,
) {
    init {
        require(rowStrideBytes >= 0) { "Texture row stride must be non-negative" }
        require(sliceStrideBytes >= 0) { "Texture slice stride must be non-negative" }
    }

    /**
     * Resolves automatic strides and computes the exact enclosing span of transferred texels.
     *
     * For a 3 x 2 RGBA8 region with row stride 16, the occupied rows are [0, 12) and [16, 28).
     * One slice needs 28 bytes, not 32. Two slices with the automatic slice stride of 32 need
     * 60 bytes, not 64. With an explicit slice stride of 28, the two slices instead need 56.
     * Gaps inside that enclosing span still are not transferred bytes.
     *
     * The result is relative to the supplied address or buffer view, not the complete buffer.
     * Image x/y/z offsets select image texels and do not skip bytes in the linear data.
     * There is one value per selected texel: multisampled images must first be resolved, or use
     * image-to-image copies that preserve the samples. A native device's pitch alignment and
     * transfer capabilities are checked separately; a footprint is not a support guarantee.
     *
     * @throws IllegalArgumentException if the region is multisampled, a stride overlaps occupied
     * data, or a resolved stride or enclosing byte span cannot be represented by [Size].
     */
    fun footprintFor(region: ImageRegion): Footprint {
        require(region.sampleCount == SampleCount.One) { "Linear texture data requires a single-sampled image" }
        val texelSize = transferTexelSizeBytes(region.format, region.aspect)
        val rowSize = multiplySize(region.width.toLong(), texelSize.toLong())
        val rowStride = rowStrideBytes.takeUnless { it == 0L } ?: rowSize
        require(rowStride >= rowSize) { "Texture row stride is smaller than the occupied row" }

        val sliceSize = addSize(multiplySize((region.height - 1).toLong(), rowStride), rowSize)
        val sliceStride = sliceStrideBytes.takeUnless { it == 0L } ?: multiplySize(rowStride, region.height.toLong())
        require(sliceStride >= sliceSize) { "Texture slice stride overlaps occupied rows of the previous slice" }

        val slices = multiplySize(region.depth.toLong(), region.arrayLayerCount.toLong())
        val requiredSize = addSize(multiplySize(slices - 1, sliceStride), sliceSize)
        return Footprint(texelSize, rowSize, rowStride, region.height, sliceStride, slices, requiredSize)
    }

    /**
     * Gives the resolved row addressing and capacity for one linear image selection.
     *
     * [requiredSizeBytes] ends immediately after the final occupied row. It is the minimum
     * capacity from the selected starting address, not the sum of useful texel bytes and not
     * the GPU texture allocation size. Iterate slices, then rows using [rowOffsetBytes] and
     * transfer [rowSizeBytes] bytes at each start; copying the entire enclosing span would
     * incorrectly consume or overwrite the gaps.
     *
     * Instances are produced by [footprintFor], which checks the addressing arithmetic once.
     */
    class Footprint internal constructor(
        val texelSizeBytes: Int,
        val rowSizeBytes: Size,
        val rowStrideBytes: Size,
        val rowCount: Int,
        val sliceStrideBytes: Size,
        val sliceCount: Size,
        val requiredSizeBytes: Size,
    ) {
        /**
         * Finds a row relative to the transfer's starting address or buffer view.
         *
         * Both indices are relative to the selected region, not the complete texture.
         * Slice zero means its first selected layer or first selected z plane.
         *
         * @throws IllegalArgumentException if either index is outside the footprint.
         */
        fun rowOffsetBytes(row: Int, slice: Size = 0): Size {
            require(row in 0..<rowCount) { "Texture transfer row is outside the footprint" }
            require(slice in 0..<sliceCount) { "Texture transfer slice is outside the footprint" }
            return slice * sliceStrideBytes + row.toLong() * rowStrideBytes
        }

        /**
         * Checks the enclosing linear capacity without requiring trailing row or slice padding.
         * A larger capacity is allowed; bytes outside the occupied rows remain unselected.
         *
         * @throws IllegalArgumentException if the capacity does not contain all selected texels.
         */
        fun validateCapacity(capacityBytes: Size) {
            require(capacityBytes >= requiredSizeBytes) { "Texture transfer needs $requiredSizeBytes bytes, but the selected range contains $capacityBytes" }
        }
    }

    companion object {
        val TightlyPacked = TextureDataLayout()
    }
}

private fun transferTexelSizeBytes(format: TextureFormat, aspect: TextureAspect): Int {
    val present = when (aspect) {
        TextureAspect.Color -> format.isColor
        TextureAspect.Depth -> format.hasDepth
        TextureAspect.Stencil -> format.hasStencil
    }
    require(present) { "Texture format $format does not contain $aspect" }
    return when (format) {
        TextureFormat.R8UnsignedNormalized -> 1
        TextureFormat.Rg8UnsignedNormalized, TextureFormat.R16Float -> 2
        TextureFormat.Rgba8UnsignedNormalized, TextureFormat.Rgba8UnsignedNormalizedSrgb,
        TextureFormat.Bgra8UnsignedNormalized, TextureFormat.Bgra8UnsignedNormalizedSrgb,
        TextureFormat.Rg16Float, TextureFormat.R32Float,
        TextureFormat.Depth24UnsignedNormalized, TextureFormat.Depth32Float,
            -> 4

        TextureFormat.Rgba16Float, TextureFormat.Rg32Float -> 8
        TextureFormat.Rgba32Float -> 16
        TextureFormat.Depth24UnsignedNormalizedStencil8,
        TextureFormat.Depth32FloatStencil8UnsignedInteger,
            -> if (aspect == TextureAspect.Stencil) 1 else 4
    }
}

private fun multiplySize(first: Size, second: Size): Size {
    require(first == 0L || second <= Long.MAX_VALUE / first) { "Texture transfer byte calculation exceeds Size" }
    return first * second
}

private fun addSize(first: Size, second: Size): Size {
    require(second <= Long.MAX_VALUE - first) { "Texture transfer byte calculation exceeds Size" }
    return first + second
}
