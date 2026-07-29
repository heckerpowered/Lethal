/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.render.opengl.OpenGLTextureRenderTarget
import net.minecraft.client.shader.Framebuffer

internal fun Framebuffer.asRenderTarget(): OpenGLTextureRenderTarget {
    return ForgeGraphics.Device.externalTextureRenderTarget(framebufferObject, framebufferTexture, depthBuffer, framebufferWidth, framebufferHeight)
}
