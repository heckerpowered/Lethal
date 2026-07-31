/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.FramebufferName
import heckerpowered.render.opengl.RenderbufferName
import heckerpowered.render.opengl.TextureName

/**
 * Creates and configures framebuffer and renderbuffer objects using the combined
 * framebuffer target required by the current backend.
 *
 * Core, `ARB_framebuffer_object`, and `EXT_framebuffer_object` entry points are
 * normalized to this one semantic interface by adapters.
 */
interface OpenGLFramebufferFunctions {
    fun createFramebuffer(): FramebufferName
    fun getBoundFramebuffer(): FramebufferName
    fun bindFramebuffer(framebuffer: FramebufferName)
    fun framebufferTexture2D(attachment: Int, texture: TextureName, level: Int)
    fun checkFramebufferStatus(): Int
    fun deleteFramebuffer(framebuffer: FramebufferName)

    fun createRenderbuffer(): RenderbufferName
    fun getBoundRenderbuffer(): RenderbufferName
    fun bindRenderbuffer(renderbuffer: RenderbufferName)
    fun renderbufferStorage(internalFormat: Int, width: Int, height: Int)
    fun framebufferRenderbuffer(attachment: Int, renderbuffer: RenderbufferName)
    fun deleteRenderbuffer(renderbuffer: RenderbufferName)
}