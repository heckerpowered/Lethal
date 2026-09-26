/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.resource.target.RenderAttachment

/** References an existing selection. The temporary framebuffer that uses it owns neither view nor image. */
internal class OpenGLRenderAttachment(val view: OpenGLTextureView) : RenderAttachment {
    val texture: OpenGLTexture get() = view.texture
    override val width get() = view.width
    override val height get() = view.height
    override val format get() = view.format
    override val sampleCount get() = texture.sampleCount
    override val arrayLayerCount get() = view.arrayLayerCount
    override val aspects get() = view.aspects
}
