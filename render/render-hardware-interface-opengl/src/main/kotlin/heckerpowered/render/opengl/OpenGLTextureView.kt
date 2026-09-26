/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.resource.texture.GpuTextureView
import heckerpowered.render.resource.texture.TextureViewDescription

/** The supported full-image view needs no additional native object or independent close operation. */
internal class OpenGLTextureView(
    override val texture: OpenGLTexture,
    private val description: TextureViewDescription,
) : GpuTextureView {
    override val dimension get() = description.dimension
    override val aspects get() = description.aspects
    override val baseMipLevel get() = description.baseMipLevel
    override val mipLevelCount get() = description.mipLevelCount
    override val baseArrayLayer get() = description.baseArrayLayer
    override val arrayLayerCount get() = description.arrayLayerCount
}
