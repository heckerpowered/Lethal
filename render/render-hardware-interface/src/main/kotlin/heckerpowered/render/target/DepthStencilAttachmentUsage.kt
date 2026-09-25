/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.target

import heckerpowered.render.texture.TextureFormat

/**
 * Selects how a render target obtains its depth/stencil attachment.
 */
sealed class DepthStencilAttachmentUsage {
    data object None : DepthStencilAttachmentUsage()

    /**
     * Creates a new depth/stencil attachment using exactly [format].
     *
     * Its initial contents are undefined. A render pass must clear an aspect or completely overwrite it
     * before attempting to preserve and use its previous contents.
     */
    data class Create(val format: TextureFormat) : DepthStencilAttachmentUsage() {
        init {
            require(format.hasDepth || format.hasStencil) { "$format cannot be used as a depth/stencil attachment" }
        }
    }

    /**
     * Reuses [attachment] without copying its contents.
     *
     * Clears and writes performed through any render target using this attachment are visible to later
     * render passes using the others.
     *
     * Render-target creation fails if the attachment comes from another graphics device, or if its
     * dimensions or sample count do not match the new target.
     */
    data class Share(val attachment: DepthStencilAttachment) : DepthStencilAttachmentUsage()
}