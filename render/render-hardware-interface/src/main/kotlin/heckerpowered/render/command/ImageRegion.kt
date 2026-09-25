/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.command

import heckerpowered.render.buffer.BufferUsage
import heckerpowered.render.buffer.GpuBufferView
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.target.RenderAttachment
import heckerpowered.render.target.validateMetadata
import heckerpowered.render.texture.*

/**
 * Selects existing image contents for an upload, copy, resolve, or discard.
 *
 * Rendering may update one cell of an atlas, while the next operation resolves that cell into
 * another image. The operation needs its own coordinates; it must not inherit the preceding
 * pass's render area, viewport, or scissor. This value selects one aspect of one mip, a consecutive
 * range of array layers, and a box of texels in each layer. Every sample of those texels is included.
 *
 * [Texture] addresses storage without creating a shader view or requesting attachment access.
 * [View] stays within an existing view and retains it so its access restrictions still apply.
 * [Attachment] addresses host outputs that need not expose a texture. These alternatives name
 * existing contents; none allocates image storage or changes the underlying resource's identity.
 *
 * Coordinates are integer texel edges in the selected mip, not normalized sampling coordinates.
 * Planar images use the RHI framebuffer convention: (0, 0) is the upper-left edge, x grows rightward,
 * and y downward. Cube faces are addressed as layers, not by a direction. The z axis selects
 * positions inside a volume and is independent of the array-layer index.
 *
 * Regions are non-empty and must fit in their referenced selection. Invalid bounds are rejected,
 * not clipped. Construction and [subRegion] do not copy or initialize contents, record a command,
 * or acquire permissions. A region reference does not extend the source's validity or access scope.
 * Device identity, native support, aliasing, and content lifetime remain command-time checks.
 */
sealed class ImageRegion private constructor(
    val aspect: TextureAspect,
    val x: Int,
    val y: Int,
    val z: Int,
    val width: Int,
    val height: Int,
    val depth: Int,
    /** First layer relative to the referenced texture, view, or attachment, respectively. */
    val baseArrayLayer: Int,
    val arrayLayerCount: Int,
) {
    abstract val format: TextureFormat
    abstract val sampleCount: SampleCount

    init {
        require(x >= 0 && y >= 0 && z >= 0) { "Image region offsets must be non-negative" }
        require(width > 0 && height > 0 && depth > 0) { "Image region extents must be positive" }
        require(baseArrayLayer >= 0) { "Image region base array layer must be non-negative" }
        require(arrayLayerCount > 0) { "Image region array layer count must be positive" }
    }

    /**
     * Selects contents directly from texture storage, without establishing a GPU view.
     *
     * A resolve destination later used by a shader can declare ResolveDestination and Sampled
     * without ColorAttachment. This selection lets resolve name that storage directly instead
     * of first asking for a drawing attachment it does not need.
     *
     * [mipLevel] and [baseArrayLayer] refer to the complete texture. Extents default to the full
     * selected mip and the layer count to one. Nonzero offsets normally require explicit extents;
     * defaults do not silently shrink to fit. Volumes use z/depth and one array layer.
     *
     * @throws IllegalArgumentException if the aspect, mip, layers, or box do not fit the texture.
     */
    class Texture(
        val texture: GpuTexture,
        val mipLevel: Int = 0,
        aspect: TextureAspect = TextureAspect.Color,
        baseArrayLayer: Int = 0,
        arrayLayerCount: Int = 1,
        x: Int = 0,
        y: Int = 0,
        z: Int = 0,
        width: Int = texture.widthAtMipLevel(mipLevel),
        height: Int = texture.heightAtMipLevel(mipLevel),
        depth: Int = texture.depthAtMipLevel(mipLevel),
    ) : ImageRegion(aspect, x, y, z, width, height, depth, baseArrayLayer, arrayLayerCount) {
        override val format: TextureFormat get() = texture.format
        override val sampleCount: SampleCount get() = texture.sampleCount

        init {
            validateTextureRegion(this)
        }
    }

    /**
     * Selects contents through a view without escaping its mip, layer, or aspect selection.
     *
     * [relativeMipLevel] is explicit: a multi-mip view does not identify one image on its own.
     * For a view starting at texture mip 2 and layer 4, relative mip 1 and base layer 2 select
     * texture mip 3 and layer 6. Spatial offsets are texels of that selected mip.
     *
     * The original [view] is retained. Backends must validate its access scope as well as the
     * texture's; reducing this operand to a bare texture must not discard import restrictions.
     * Default extents cover the selected mip, while the layer count defaults to one.
     *
     * @throws IllegalArgumentException if the view metadata is inconsistent or the region exceeds
     * the view. In particular, a depth-only view cannot select stencil from its source texture.
     */
    class View(
        val view: GpuTextureView,
        val relativeMipLevel: Int,
        aspect: TextureAspect = TextureAspect.Color,
        baseArrayLayer: Int = 0,
        arrayLayerCount: Int = 1,
        x: Int = 0,
        y: Int = 0,
        z: Int = 0,
        width: Int = view.texture.widthAtMipLevel(view.absoluteMipLevel(relativeMipLevel)),
        height: Int = view.texture.heightAtMipLevel(view.absoluteMipLevel(relativeMipLevel)),
        depth: Int = view.texture.depthAtMipLevel(view.absoluteMipLevel(relativeMipLevel)),
    ) : ImageRegion(aspect, x, y, z, width, height, depth, baseArrayLayer, arrayLayerCount) {
        override val format: TextureFormat get() = view.format
        override val sampleCount: SampleCount get() = view.texture.sampleCount

        /** Selected mip in the complete texture rather than in the view's numbering. */
        val textureMipLevel: Int get() = view.baseMipLevel + relativeMipLevel

        /** First selected layer in the complete texture. */
        val textureBaseArrayLayer: Int get() = view.baseArrayLayer + baseArrayLayer

        init {
            validateViewRegion(this)
        }
    }

    /**
     * Selects contents exposed by an attachment, including an opaque host-provided output.
     *
     * The attachment already selects its mip and layers. [baseArrayLayer] is relative to that
     * selection; it cannot reach an earlier layer or an aspect the attachment does not expose.
     * The rectangle defaults to the whole attached image and the layer count to one.
     *
     * Attachment access alone does not establish resolve access. The device checks the operation
     * against its resource record and import contract. A texture-derived attachment still requires
     * the original texture's ResolveSource or ResolveDestination usage. Wrapping it does not grant
     * a missing role.
     *
     * @throws IllegalArgumentException if the attachment metadata, aspect, layers, or box are invalid.
     */
    class Attachment(
        val attachment: RenderAttachment,
        aspect: TextureAspect = TextureAspect.Color,
        baseArrayLayer: Int = 0,
        arrayLayerCount: Int = 1,
        x: Int = 0,
        y: Int = 0,
        width: Int = attachment.width,
        height: Int = attachment.height,
    ) : ImageRegion(aspect, x, y, 0, width, height, 1, baseArrayLayer, arrayLayerCount) {
        override val format: TextureFormat get() = attachment.format
        override val sampleCount: SampleCount get() = attachment.sampleCount

        init {
            validateAttachmentRegion(this)
        }
    }

    /**
     * Selects a smaller box and layer range, keeping the same source and aspect.
     *
     * Offsets are relative to this region: relativeX = 8 from a region beginning at x = 64
     * addresses x = 72 in its source. Extra space elsewhere in the source cannot extend this
     * parent's bounds. Width and height are explicit; depth and layer count default to one.
     * Returned coordinates and layer indices remain relative to the original source reference.
     *
     * @throws IllegalArgumentException if a requested interval is empty or outside this region.
     */
    fun subRegion(relativeX: Int = 0, relativeY: Int = 0, relativeZ: Int = 0, width: Int, height: Int, depth: Int = 1, relativeBaseArrayLayer: Int = 0, arrayLayerCount: Int = 1): ImageRegion {
        requireInterval(this.width, relativeX, width, "Child x range")
        requireInterval(this.height, relativeY, height, "Child y range")
        requireInterval(this.depth, relativeZ, depth, "Child z range")
        requireInterval(this.arrayLayerCount, relativeBaseArrayLayer, arrayLayerCount, "Child layer range")

        return when (this) {
            is Texture -> Texture(texture, mipLevel, aspect, baseArrayLayer + relativeBaseArrayLayer, arrayLayerCount, x + relativeX, y + relativeY, z + relativeZ, width, height, depth)
            is View -> View(view, relativeMipLevel, aspect, baseArrayLayer + relativeBaseArrayLayer, arrayLayerCount, x + relativeX, y + relativeY, z + relativeZ, width, height, depth)
            is Attachment -> Attachment(attachment, aspect, baseArrayLayer + relativeBaseArrayLayer, arrayLayerCount, x + relativeX, y + relativeY, width, height)
        }
    }

    /**
     * Checks metadata requirements for resolving this region into [destination].
     *
     * Equal extents mean one destination pixel per source pixel; equal layer counts pair the
     * selections in order. Complete image sizes, mip numbers, and layer bases need not match.
     * This command uses two-dimensional color regions, a multisampled source, and a single-sampled
     * destination. Depth/stencil resolve and volume destinations are outside this command's contract.
     *
     * Texture and view operands must declare the corresponding resolve roles. Opaque attachments
     * additionally need checks against the device's resource record. This function cannot prove
     * non-overlap from reference identity, establish device support, or check content availability.
     * Backends must check those conditions before accepting the command; this is not a support query.
     *
     * @throws IllegalArgumentException if aspects, formats, extents, layers, sample counts,
     * dimensionality, or publicly available usage declarations contradict a color resolve.
     */
    fun validateResolveTo(destination: ImageRegion) {
        require(aspect == TextureAspect.Color && destination.aspect == TextureAspect.Color) { "Color resolve requires the Color aspect at both endpoints" }
        require(format == destination.format) { "Resolve formats must match: $format and ${destination.format}" }
        require(width == destination.width && height == destination.height && depth == destination.depth) { "Resolve extents must match; resolve does not scale images" }
        require(arrayLayerCount == destination.arrayLayerCount) { "Resolve layer counts must match" }
        require(sampleCount != SampleCount.One) { "Resolve source must be multisampled" }
        require(destination.sampleCount == SampleCount.One) { "Resolve destination must be single-sampled" }

        validatePlanarResolve(this)
        validatePlanarResolve(destination)
        validateResolveUsage(this, TextureUsage.ResolveSource)
        validateResolveUsage(destination, TextureUsage.ResolveDestination)
    }

    /**
     * Checks a host upload's destination role, linear layout, and nonzero source address.
     *
     * The returned footprint describes the occupied source rows, not a contiguous block that
     * may be copied including padding. This does not read memory or prove address validity.
     * For opaque attachments, the device must obtain permissions from its resource record.
     * Device identity, native support, imported scopes, and content lifetime remain backend checks.
     *
     * @throws IllegalArgumentException if transfer metadata is invalid or the address is zero.
     */
    fun validateUpload(sourceAddress: NativeAddress, sourceLayout: TextureDataLayout = TextureDataLayout.TightlyPacked): TextureDataLayout.Footprint {
        val footprint = validateLinearTransfer(TextureUsage.TransferDestination, sourceLayout)
        require(sourceAddress.rawValue != 0L) { "A nonempty texture upload requires a nonzero source address" }
        return footprint
    }

    /**
     * Checks the source buffer selection against the rows needed to populate this image region.
     *
     * Source capacity may exceed the footprint, but bytes outside occupied rows are not input.
     * Both transfer roles are checked where exposed. The device must additionally check native
     * offsets/pitches and whether the buffer and image alias overlapping physical storage.
     *
     * @throws IllegalArgumentException if usage, sample count, layout, or source capacity is invalid.
     */
    fun validateCopyFromBuffer(source: GpuBufferView, sourceLayout: TextureDataLayout = TextureDataLayout.TightlyPacked): TextureDataLayout.Footprint {
        require(BufferUsage.TransferSource in source.buffer.usage) { "Texture upload requires BufferUsage.TransferSource" }
        val footprint = validateLinearTransfer(TextureUsage.TransferDestination, sourceLayout)
        footprint.validateCapacity(source.sizeBytes)
        return footprint
    }

    /**
     * Checks a linear buffer destination for this region's texel data, excluding gaps and tail padding.
     *
     * Only occupied destination rows are replaced. Native resource, pitch, aliasing, and scope
     * checks remain necessary, and successful validation does not make the buffer CPU-readable.
     *
     * @throws IllegalArgumentException if usage, sample count, layout, or destination capacity is invalid.
     */
    fun validateCopyToBuffer(destination: GpuBufferView, destinationLayout: TextureDataLayout = TextureDataLayout.TightlyPacked): TextureDataLayout.Footprint {
        require(BufferUsage.TransferDestination in destination.buffer.usage) { "Texture readback requires BufferUsage.TransferDestination" }
        val footprint = validateLinearTransfer(TextureUsage.TransferSource, destinationLayout)
        footprint.validateCapacity(destination.sizeBytes)
        return footprint
    }

    /**
     * Checks whether this region can describe a direct texel copy to [destination].
     *
     * Equal extents and layer counts pair corresponding texels, z planes, and array layers.
     * Equal formats/aspects preserve their representation; equal sample counts preserve each
     * sample instead of resolving it. Different mip levels, image sizes, and starting positions
     * are allowed. No automatic conversion between the z axis and the array-layer axis occurs.
     *
     * Known overlap is rejected, including a texture referenced directly on one side and through
     * a view on the other. Different wrappers can still alias. Passing these checks does not prove
     * physical disjointness, device support, source validity, or an opaque attachment's permission.
     * Backends must also satisfy native depth-value restrictions without scanning GPU data here.
     *
     * @throws IllegalArgumentException if usages, formats, aspects, extents, layer/sample counts,
     * or overlap observable through the same resource contradict a direct copy.
     */
    fun validateCopyTo(destination: ImageRegion) {
        require(aspect == destination.aspect) { "Texture copy aspects must match" }
        require(format == destination.format) { "Texture copy formats must match: $format and ${destination.format}" }
        require(width == destination.width && height == destination.height && depth == destination.depth) { "Texture copy extents must match; copying does not resize or remap layers" }
        require(arrayLayerCount == destination.arrayLayerCount) { "Texture copy layer counts must match" }
        require(sampleCount == destination.sampleCount) { "Texture copy sample counts must match; use resolve to reduce samples" }
        validateTransferUsage(this, TextureUsage.TransferSource)
        validateTransferUsage(destination, TextureUsage.TransferDestination)
        validateKnownCopyOverlap(this, destination)
    }

    private fun validateLinearTransfer(usage: TextureUsage, layout: TextureDataLayout): TextureDataLayout.Footprint {
        validateTransferUsage(this, usage)
        return layout.footprintFor(this)
    }
}

/**
 * Selects one aspect of the complete range of a single-mip view for an image operation.
 *
 * All selected layers are preserved. Multi-mip views are rejected instead of being narrowed;
 * use [ImageRegion.View] with an explicit relative mip to choose one of their levels.
 *
 * @throws IllegalArgumentException if the view does not select one mip or expose [aspect].
 */
fun GpuTextureView.asImageRegion(aspect: TextureAspect = TextureAspect.Color): ImageRegion.View {
    require(mipLevelCount == 1) { "Select a relative mip explicitly for a multi-mip texture view" }
    return ImageRegion.View(this, relativeMipLevel = 0, aspect = aspect, arrayLayerCount = arrayLayerCount)
}

private fun validateTextureRegion(region: ImageRegion.Texture) {
    val texture = region.texture
    validateTextureShape(texture)
    requireAspect(texture.format, region.aspect)
    validateBox(region, texture.widthAtMipLevel(region.mipLevel), texture.heightAtMipLevel(region.mipLevel), texture.depthAtMipLevel(region.mipLevel), texture.arrayLayerCount)
}

private fun validateViewRegion(region: ImageRegion.View) {
    val view = region.view
    val texture = view.texture
    validateTextureShape(texture)
    TextureViewDescription(
        dimension = view.dimension,
        aspects = view.aspects,
        baseMipLevel = view.baseMipLevel,
        mipLevelCount = view.mipLevelCount,
        baseArrayLayer = view.baseArrayLayer,
        arrayLayerCount = view.arrayLayerCount,
    ).validateFor(texture)

    require(view.format == texture.format) { "Image operations do not reinterpret a texture view's format" }
    require(
        view.width == texture.widthAtMipLevel(view.baseMipLevel) &&
                view.height == texture.heightAtMipLevel(view.baseMipLevel) &&
                view.depth == texture.depthAtMipLevel(view.baseMipLevel)
    ) { "Texture view dimensions do not match its selected mip" }
    require(region.aspect in view.aspects) { "Texture view does not expose ${region.aspect}" }
    val mip = view.absoluteMipLevel(region.relativeMipLevel)
    validateBox(region, texture.widthAtMipLevel(mip), texture.heightAtMipLevel(mip), texture.depthAtMipLevel(mip), view.arrayLayerCount)
}

private fun validateAttachmentRegion(region: ImageRegion.Attachment) {
    val attachment = region.attachment
    attachment.validateMetadata()
    require(region.aspect in attachment.aspects) { "Attachment does not expose ${region.aspect}" }
    validateBox(region, attachment.width, attachment.height, 1, attachment.arrayLayerCount)
}

private fun validateBox(region: ImageRegion, width: Int, height: Int, depth: Int, layers: Int) {
    requireInterval(width, region.x, region.width, "Image x range")
    requireInterval(height, region.y, region.height, "Image y range")
    requireInterval(depth, region.z, region.depth, "Image z range")
    requireInterval(layers, region.baseArrayLayer, region.arrayLayerCount, "Image layer range")
}

private fun validateTextureShape(texture: GpuTexture) {
    require(texture.width > 0 && texture.height > 0 && texture.depth > 0) { "Texture dimensions must be positive" }
    require(texture.arrayLayerCount > 0) { "Texture layer count must be positive" }
    val maximumMipCount = Int.SIZE_BITS - Integer.numberOfLeadingZeros(maxOf(texture.width, texture.height, texture.depth))
    require(texture.mipLevelCount in 1..maximumMipCount) { "Texture mip count exceeds its spatial extent" }
    when (texture.dimension) {
        TextureDimension.OneDimensional -> require(texture.height == 1 && texture.depth == 1) { "One-dimensional texture storage has height and depth one" }
        TextureDimension.TwoDimensional -> require(texture.depth == 1) { "Two-dimensional texture storage has depth one" }
        TextureDimension.ThreeDimensional -> require(texture.arrayLayerCount == 1) { "Volume slices are not array layers" }
    }
    if (texture.sampleCount != SampleCount.One) {
        require(texture.dimension == TextureDimension.TwoDimensional && texture.mipLevelCount == 1) {
            "Multisampled texture storage is two-dimensional with one mip"
        }
    }
}

private fun requireAspect(format: TextureFormat, aspect: TextureAspect) {
    val exists = when (aspect) {
        TextureAspect.Color -> format.isColor
        TextureAspect.Depth -> format.hasDepth
        TextureAspect.Stencil -> format.hasStencil
    }
    require(exists) { "Texture format $format does not contain $aspect" }
}

private fun GpuTextureView.absoluteMipLevel(relativeMip: Int): Int {
    requireInterval(texture.mipLevelCount, baseMipLevel, mipLevelCount, "Texture view mip range")
    require(relativeMip in 0..<mipLevelCount) { "Relative mip is outside the texture view" }
    return baseMipLevel + relativeMip
}

private fun requireInterval(capacity: Int, offset: Int, count: Int, context: String) {
    require(capacity > 0 && offset >= 0 && offset < capacity && count > 0) { "$context must be non-empty and start inside its source" }
    // Check remaining capacity before adding endpoints so invalid input cannot wrap around.
    require(count <= capacity - offset) { "$context extends beyond its source" }
}

private fun validatePlanarResolve(region: ImageRegion) {
    val dimension = when (region) {
        is ImageRegion.Texture -> region.texture.dimension
        is ImageRegion.View -> region.view.texture.dimension
        is ImageRegion.Attachment -> TextureDimension.TwoDimensional
    }
    require(dimension == TextureDimension.TwoDimensional && region.z == 0 && region.depth == 1) { "Color resolve requires two-dimensional image regions" }
}

private fun validateResolveUsage(region: ImageRegion, usage: TextureUsage) {
    val texture = when (region) {
        is ImageRegion.Texture -> region.texture
        is ImageRegion.View -> region.view.texture
        is ImageRegion.Attachment -> return // Device/import metadata, not a cast, supplies the permission check.
    }
    require(usage in texture.usage) { "Resolve requires TextureUsage.$usage" }
}

private fun validateTransferUsage(region: ImageRegion, usage: TextureUsage) {
    val texture = when (region) {
        is ImageRegion.Texture -> region.texture
        is ImageRegion.View -> region.view.texture
        is ImageRegion.Attachment -> return // Import metadata supplies permissions; attachment identity grants none.
    }
    require(usage in texture.usage) { "Texture transfer requires TextureUsage.$usage" }
}

private fun validateKnownCopyOverlap(source: ImageRegion, destination: ImageRegion) {
    validateKnownImageDisjoint(source, destination, "Texture copy source and destination select overlapping contents")
}

/** Rejects proven overlap; passing still requires the backend to resolve opaque and physical aliases. */
internal fun validateKnownImageDisjoint(source: ImageRegion, destination: ImageRegion, message: String) {
    if (!sameKnownImage(source, destination)) return
    if (knownMip(source) != knownMip(destination)) return
    val overlap = intervalsOverlap(knownLayer(source), source.arrayLayerCount, knownLayer(destination), destination.arrayLayerCount) &&
            intervalsOverlap(source.x, source.width, destination.x, destination.width) &&
            intervalsOverlap(source.y, source.height, destination.y, destination.height) &&
            intervalsOverlap(source.z, source.depth, destination.z, destination.depth)
    require(!overlap) { message }
}

private fun sameKnownImage(source: ImageRegion, destination: ImageRegion): Boolean {
    if (source is ImageRegion.Attachment || destination is ImageRegion.Attachment) {
        return source is ImageRegion.Attachment && destination is ImageRegion.Attachment && source.attachment === destination.attachment
    }
    return knownTexture(source) === knownTexture(destination)
}

private fun knownTexture(region: ImageRegion) = when (region) {
    is ImageRegion.Texture -> region.texture
    is ImageRegion.View -> region.view.texture
    is ImageRegion.Attachment -> null
}

private fun knownMip(region: ImageRegion): Int = when (region) {
    is ImageRegion.Texture -> region.mipLevel
    is ImageRegion.View -> region.textureMipLevel
    is ImageRegion.Attachment -> 0
}

private fun knownLayer(region: ImageRegion): Int = when (region) {
    is ImageRegion.View -> region.textureBaseArrayLayer
    is ImageRegion.Texture, is ImageRegion.Attachment -> region.baseArrayLayer
}

private fun intervalsOverlap(first: Int, firstCount: Int, second: Int, secondCount: Int): Boolean =
    first.toLong() < second.toLong() + secondCount && second.toLong() < first.toLong() + firstCount
