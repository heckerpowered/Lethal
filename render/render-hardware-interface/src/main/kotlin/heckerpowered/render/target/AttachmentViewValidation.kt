/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.target

import heckerpowered.render.texture.*

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
