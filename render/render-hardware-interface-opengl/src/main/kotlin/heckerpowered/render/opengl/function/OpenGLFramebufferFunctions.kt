/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.FramebufferName
import heckerpowered.render.opengl.RenderbufferName
import heckerpowered.render.opengl.TextureName

/**
 * Creates and configures framebuffer and renderbuffer objects.
 *
 * Draw and read bindings remain distinct where OpenGL provides distinct state.
 * `EXT_framebuffer_object` adapters expose its single combined binding through
 * both operations without simulating independent state.
 *
 * Core, `ARB_framebuffer_object`, and `EXT_framebuffer_object` entry points are
 * normalized to this one semantic interface by adapters.
 */
interface OpenGLFramebufferFunctions {
    fun createFramebuffer(): FramebufferName
    fun getBoundDrawFramebuffer(): FramebufferName
    fun getBoundReadFramebuffer(): FramebufferName
    fun bindDrawFramebuffer(framebuffer: FramebufferName)
    fun bindReadFramebuffer(framebuffer: FramebufferName)
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

context(function: OpenGLFramebufferFunctions)
fun createFramebuffer(): FramebufferName = function.createFramebuffer()

context(function: OpenGLFramebufferFunctions)
fun getBoundDrawFramebuffer(): FramebufferName = function.getBoundDrawFramebuffer()

context(function: OpenGLFramebufferFunctions)
fun getBoundReadFramebuffer(): FramebufferName = function.getBoundReadFramebuffer()

context(function: OpenGLFramebufferFunctions)
fun bindDrawFramebuffer(framebuffer: FramebufferName) = function.bindDrawFramebuffer(framebuffer)

context(function: OpenGLFramebufferFunctions)
fun bindReadFramebuffer(framebuffer: FramebufferName) = function.bindReadFramebuffer(framebuffer)

context(function: OpenGLFramebufferFunctions)
fun framebufferTexture2D(attachment: Int, texture: TextureName, level: Int) =
    function.framebufferTexture2D(attachment, texture, level)

context(function: OpenGLFramebufferFunctions)
fun checkFramebufferStatus(): Int = function.checkFramebufferStatus()

context(function: OpenGLFramebufferFunctions)
fun deleteFramebuffer(framebuffer: FramebufferName) = function.deleteFramebuffer(framebuffer)

context(function: OpenGLFramebufferFunctions)
fun createRenderbuffer(): RenderbufferName = function.createRenderbuffer()

context(function: OpenGLFramebufferFunctions)
fun getBoundRenderbuffer(): RenderbufferName = function.getBoundRenderbuffer()

context(function: OpenGLFramebufferFunctions)
fun bindRenderbuffer(renderbuffer: RenderbufferName) = function.bindRenderbuffer(renderbuffer)

context(function: OpenGLFramebufferFunctions)
fun renderbufferStorage(internalFormat: Int, width: Int, height: Int) =
    function.renderbufferStorage(internalFormat, width, height)

context(function: OpenGLFramebufferFunctions)
fun framebufferRenderbuffer(attachment: Int, renderbuffer: RenderbufferName) =
    function.framebufferRenderbuffer(attachment, renderbuffer)

context(function: OpenGLFramebufferFunctions)
fun deleteRenderbuffer(renderbuffer: RenderbufferName) = function.deleteRenderbuffer(renderbuffer)
