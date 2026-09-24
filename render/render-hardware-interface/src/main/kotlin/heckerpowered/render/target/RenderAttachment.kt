/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.target

import heckerpowered.render.pass.RenderPassAttachment
import heckerpowered.render.pass.RenderPassDescription
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.texture.*

/**
 * Exposes an image region for use as a color, depth, or stencil attachment while drawing.
 *
 * A color attachment receives the fragment colors produced by a draw. Depth and stencil
 * attachments supply the values used by their tests and receive any enabled updates. This
 * interface identifies the images involved; the pipeline determines how drawing accesses them.
 *
 * The same attachment can be cleared for a scene pass and then loaded by an overlay pass.
 * [RenderPassAttachment] supplies those per-pass operations separately from the attachment.
 *
 * An attachment may expose both depth and stencil. Its position in [RenderPassDescription]
 * selects which aspect that use addresses. Using one attachment in both positions allows depth
 * to be loaded and stored while stencil is independently cleared and discarded.
 *
 * Texture-backed attachments refer to the storage and subresources selected by a [GpuTextureView].
 * Host-provided outputs can also supply attachments without exposing a texture-view interface.
 * Neither kind implicitly selects a resolve destination or a final output image.
 *
 * Image operations can address this selection without requiring a texture-view cast. The device
 * uses the same underlying resource record, preserving usage and import restrictions. Attachment
 * access alone does not grant resolve access: texture-derived attachments require the original
 * texture's resolve usage, and opaque host images require the corresponding admitted operation.
 *
 * Metadata describes this particular image selection and remains unchanged while it is valid.
 * If a host replaces its output storage, the old attachment is invalidated rather than silently
 * retargeted to an unrelated image. Device implementations retain the underlying storage and
 * subresource identity so aliases can be recognized independently of Kotlin object identity.
 */
interface RenderAttachment {
    /** Width of the selected image in texels, not necessarily the width of its complete texture. */
    val width: Int

    /** Height of the selected image in texels. A one-dimensional image has height one. */
    val height: Int

    /**
     * Representation of the selected image data.
     *
     * A depth-only selection of combined depth-stencil storage still reports the combined
     * format. [aspects] determines which parts can be addressed through this attachment.
     */
    val format: TextureFormat

    /**
     * Samples stored at each image position.
     *
     * This is a property of the attached image, not a request to change the pipeline. Ordinary
     * drawing requires matching direct attachments and rasterization sample counts.
     */
    val sampleCount: SampleCount

    /**
     * Number of consecutive image layers exposed for attachment access.
     *
     * Layer zero is the first layer in this attachment's selection, which may be a nonzero
     * layer of a larger texture. Cube-derived attachments count individual faces here. The pass
     * explicitly chooses how many leading layers to use; this count does not replicate draws.
     */
    val arrayLayerCount: Int

    /**
     * Image data that can be used through this attachment: Color, Depth, Stencil, or Depth and
     * Stencil together.
     *
     * A pass position requires membership, not an exact set match. An attachment exposing both
     * Depth and Stencil can be used in either position without operating on the other aspect.
     * Implementations expose a stable, unmodifiable set consistent with [format].
     */
    val aspects: Set<TextureAspect>
}

internal fun RenderAttachment.validateMetadata() {
    require(width > 0 && height > 0) { "Render attachment dimensions must be positive" }
    require(arrayLayerCount > 0) { "Render attachment layer count must be positive" }
    require(aspects.isNotEmpty()) { "Render attachment requires at least one aspect" }
    require(TextureAspect.Color !in aspects || aspects.size == 1) {
        "Color cannot be combined with depth or stencil in a render attachment"
    }
    for (aspect in aspects) {
        val present = when (aspect) {
            TextureAspect.Color -> format.isColor
            TextureAspect.Depth -> format.hasDepth
            TextureAspect.Stencil -> format.hasStencil
        }
        require(present) { "Attachment format $format does not contain the $aspect aspect" }
    }
}

/**
 * Checks whether a texture view's reported selection permits attachment access.
 *
 * Backends use these shared checks inside attachment creation. A single mip is required, and
 * attachment usage must already be enabled: Color requires [TextureUsage.ColorAttachment],
 * while Depth or Stencil requires [TextureUsage.DepthStencilAttachment]. Shader sampling is
 * not a prerequisite for rendering into an image.
 *
 * Array selections remain layered selections. Cube and cube-array views contribute their
 * selected faces in array-layer order; this operation does not choose a face from a direction.
 * A one-dimensional image can be represented as a single row where the backend supports it.
 * A volume view cannot implicitly choose a two-dimensional slice; that needs a separate slice
 * selection model and is not provided by this conversion.
 *
 * This function neither creates an attachment nor checks device identity, resource validity,
 * native representation support, or compatibility with a particular pass. Returning normally
 * is not a substitute for those checks in the device implementation.
 *
 * @throws IllegalArgumentException if the view's metadata is inconsistent, it selects more than
 * one mip level, or its texture lacks the required attachment usage.
 * @throws UnsupportedOperationException if the view selects a three-dimensional volume.
 */
fun validateAttachmentView(view: GpuTextureView) {
    val texture = view.texture
    TextureViewDescription(
        dimension = view.dimension,
        aspects = view.aspects,
        baseMipLevel = view.baseMipLevel,
        mipLevelCount = view.mipLevelCount,
        baseArrayLayer = view.baseArrayLayer,
        arrayLayerCount = view.arrayLayerCount,
    ).validateFor(texture)

    require(view.mipLevelCount == 1) { "A render attachment must select exactly one mip level" }
    require(view.format == texture.format) { "Attachment conversion does not reinterpret the texture format" }
    require(
        view.width == texture.widthAtMipLevel(view.baseMipLevel) &&
                view.height == texture.heightAtMipLevel(view.baseMipLevel) &&
                view.depth == texture.depthAtMipLevel(view.baseMipLevel)
    ) {
        "Texture view dimensions do not match its selected mip level"
    }

    for (aspect in view.aspects) {
        val requiredUsage = when (aspect) {
            TextureAspect.Color -> TextureUsage.ColorAttachment
            TextureAspect.Depth, TextureAspect.Stencil -> TextureUsage.DepthStencilAttachment
        }
        require(requiredUsage in texture.usage) { "Attachment access to $aspect requires TextureUsage.$requiredUsage" }
    }

    if (view.dimension == TextureViewDimension.ThreeDimensional) {
        throw UnsupportedOperationException("A volume view requires an explicit slice selection before two-dimensional attachment access")
    }
}