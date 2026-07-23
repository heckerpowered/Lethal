/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import net.minecraft.client.renderer.OpenGlHelper
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBTextureFloat
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLContext
import java.nio.ByteBuffer

internal interface ColorRenderTarget {
    val framebufferObject: Int
    val colorTexture: Int
    val width: Int
    val height: Int
}

internal data class ExternalColorRenderTarget(
    override val framebufferObject: Int,
    override val colorTexture: Int,
    override val width: Int,
    override val height: Int,
) : ColorRenderTarget

internal data class RenderSurface(val framebufferObject: Int, val width: Int, val height: Int)

internal data class BoundRenderSurface(
    private val framebufferObject: Int,
    private val viewportX: Int,
    private val viewportY: Int,
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) {
    companion object {
        fun capture(): BoundRenderSurface {
            val viewport = BufferUtils.createIntBuffer(16)
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport)
            return BoundRenderSurface(
                framebufferObject = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                viewportX = viewport.get(0),
                viewportY = viewport.get(1),
                viewportWidth = viewport.get(2),
                viewportHeight = viewport.get(3),
            )
        }
    }

    fun restore() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferObject)
        GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight)
    }
}

internal class ManagedColorRenderTarget(override val width: Int, override val height: Int) : ColorRenderTarget, AutoCloseable {
    override var framebufferObject = -1
        private set
    override var colorTexture = -1
        private set
    val colorInternalFormat: Int
    private var depthRenderbuffer = -1

    init {
        require(width > 0 && height > 0) { "Framebuffer dimensions must be positive" }
        colorInternalFormat = selectColorInternalFormat()
        createAttachments()
    }

    override fun close() {
        if (depthRenderbuffer >= 0) {
            OpenGlHelper.glDeleteRenderbuffers(depthRenderbuffer)
            depthRenderbuffer = -1
        }
        if (colorTexture >= 0) {
            GL11.glDeleteTextures(colorTexture)
            colorTexture = -1
        }
        if (framebufferObject >= 0) {
            OpenGlHelper.glDeleteFramebuffers(framebufferObject)
            framebufferObject = -1
        }
    }

    fun withDepthRenderbuffer(depthRenderbufferObject: Int, operation: () -> Unit) {
        if (depthRenderbufferObject < 0) {
            operation()
            return
        }

        replaceDepthRenderbuffer(depthRenderbufferObject)
        try {
            requireCompleteFramebuffer(depthRenderbufferObject)
            operation()
        } finally {
            replaceDepthRenderbuffer(depthRenderbuffer)
            requireCompleteFramebuffer(depthRenderbuffer)
        }
    }

    private fun createAttachments() {
        framebufferObject = OpenGlHelper.glGenFramebuffers()
        colorTexture = GL11.glGenTextures()
        depthRenderbuffer = OpenGlHelper.glGenRenderbuffers()

        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, colorInternalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null as ByteBuffer?)

            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferObject)
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0)
            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, depthRenderbuffer)
            OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height)
            OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, depthRenderbuffer)

            val framebufferStatus = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)
            check(framebufferStatus == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) { "Bloom framebuffer is incomplete after creation: status=$framebufferStatus, framebuffer=$framebufferObject, colorTexture=$colorTexture, depthRenderbuffer=$depthRenderbuffer, colorInternalFormat=$colorInternalFormat, size=${width}x$height" }
        } catch (throwable: Throwable) {
            close()
            throw throwable
        }
    }

    private fun replaceDepthRenderbuffer(depthRenderbufferObject: Int) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferObject)
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, depthRenderbufferObject)
    }

    private fun requireCompleteFramebuffer(attachedDepthRenderbuffer: Int) {
        val framebufferStatus = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)
        check(framebufferStatus == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) { "Bloom framebuffer is incomplete after changing its depth attachment: status=$framebufferStatus, framebuffer=$framebufferObject, depthRenderbuffer=$attachedDepthRenderbuffer, colorInternalFormat=$colorInternalFormat, size=${width}x$height" }
    }

    private fun selectColorInternalFormat(): Int {
        val capabilities = GLContext.getCapabilities()
        if (capabilities.OpenGL30 || capabilities.GL_ARB_texture_float && capabilities.GL_ARB_color_buffer_float) {
            return ARBTextureFloat.GL_RGBA16F_ARB
        }
        return GL11.GL_RGBA8
    }
}
