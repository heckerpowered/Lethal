/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl3

import heckerpowered.render.opengl.FramebufferName
import heckerpowered.render.opengl.RenderbufferName
import heckerpowered.render.opengl.TextureName
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import org.lwjgl.opengl.*

internal class Lwjgl3FramebufferFunctions private constructor(
    private val entryPoints: Lwjgl3FramebufferEntryPoints,
) : OpenGLFramebufferFunctions {
    private val framebufferTarget = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_FRAMEBUFFER
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_FRAMEBUFFER
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_EXT
    }
    private val drawFramebufferTarget = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_DRAW_FRAMEBUFFER
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_DRAW_FRAMEBUFFER
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_EXT
    }
    private val readFramebufferTarget = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_READ_FRAMEBUFFER
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_READ_FRAMEBUFFER
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_EXT
    }
    private val drawFramebufferBinding = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_DRAW_FRAMEBUFFER_BINDING
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
    }
    private val readFramebufferBinding = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_READ_FRAMEBUFFER_BINDING
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_READ_FRAMEBUFFER_BINDING
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
    }
    private val renderbufferTarget = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_RENDERBUFFER
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_RENDERBUFFER
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_RENDERBUFFER_EXT
    }
    private val renderbufferBinding = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.GL_RENDERBUFFER_BINDING
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_RENDERBUFFER_BINDING
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_RENDERBUFFER_BINDING_EXT
    }

    override fun createFramebuffer(): FramebufferName {
        val name = when (entryPoints) {
            Lwjgl3FramebufferEntryPoints.Core -> GL30.glGenFramebuffers()
            Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glGenFramebuffers()
            Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glGenFramebuffersEXT()
        }
        return FramebufferName(name)
    }

    override fun getBoundDrawFramebuffer(): FramebufferName = FramebufferName(GL11.glGetInteger(drawFramebufferBinding))

    override fun getBoundReadFramebuffer(): FramebufferName = FramebufferName(GL11.glGetInteger(readFramebufferBinding))

    override fun bindDrawFramebuffer(framebuffer: FramebufferName) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glBindFramebuffer(drawFramebufferTarget, framebuffer.value)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glBindFramebuffer(drawFramebufferTarget, framebuffer.value)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glBindFramebufferEXT(drawFramebufferTarget, framebuffer.value)
    }

    override fun bindReadFramebuffer(framebuffer: FramebufferName) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glBindFramebuffer(readFramebufferTarget, framebuffer.value)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glBindFramebuffer(readFramebufferTarget, framebuffer.value)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glBindFramebufferEXT(readFramebufferTarget, framebuffer.value)
    }

    override fun framebufferTexture2D(attachment: Int, texture: TextureName, level: Int) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glFramebufferTexture2D(framebufferTarget, attachment, GL11.GL_TEXTURE_2D, texture.value, level)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glFramebufferTexture2D(framebufferTarget, attachment, GL11.GL_TEXTURE_2D, texture.value, level)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glFramebufferTexture2DEXT(framebufferTarget, attachment, GL11.GL_TEXTURE_2D, texture.value, level)
    }

    override fun checkFramebufferStatus(): Int = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glCheckFramebufferStatus(framebufferTarget)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glCheckFramebufferStatus(framebufferTarget)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glCheckFramebufferStatusEXT(framebufferTarget)
    }

    override fun deleteFramebuffer(framebuffer: FramebufferName) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glDeleteFramebuffers(framebuffer.value)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glDeleteFramebuffers(framebuffer.value)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer.value)
    }

    override fun createRenderbuffer(): RenderbufferName {
        val name = when (entryPoints) {
            Lwjgl3FramebufferEntryPoints.Core -> GL30.glGenRenderbuffers()
            Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glGenRenderbuffers()
            Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glGenRenderbuffersEXT()
        }
        return RenderbufferName(name)
    }

    override fun getBoundRenderbuffer(): RenderbufferName = RenderbufferName(GL11.glGetInteger(renderbufferBinding))

    override fun bindRenderbuffer(renderbuffer: RenderbufferName) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glBindRenderbuffer(renderbufferTarget, renderbuffer.value)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glBindRenderbuffer(renderbufferTarget, renderbuffer.value)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glBindRenderbufferEXT(renderbufferTarget, renderbuffer.value)
    }

    override fun renderbufferStorage(internalFormat: Int, width: Int, height: Int) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glRenderbufferStorage(renderbufferTarget, internalFormat, width, height)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glRenderbufferStorage(renderbufferTarget, internalFormat, width, height)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glRenderbufferStorageEXT(renderbufferTarget, internalFormat, width, height)
    }

    override fun framebufferRenderbuffer(attachment: Int, renderbuffer: RenderbufferName) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glFramebufferRenderbuffer(framebufferTarget, attachment, renderbufferTarget, renderbuffer.value)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glFramebufferRenderbuffer(framebufferTarget, attachment, renderbufferTarget, renderbuffer.value)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glFramebufferRenderbufferEXT(framebufferTarget, attachment, renderbufferTarget, renderbuffer.value)
    }

    override fun deleteRenderbuffer(renderbuffer: RenderbufferName) = when (entryPoints) {
        Lwjgl3FramebufferEntryPoints.Core -> GL30.glDeleteRenderbuffers(renderbuffer.value)
        Lwjgl3FramebufferEntryPoints.ARB -> ARBFramebufferObject.glDeleteRenderbuffers(renderbuffer.value)
        Lwjgl3FramebufferEntryPoints.EXT -> EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer.value)
    }

    companion object {
        fun create(capabilities: GLCapabilities): OpenGLFramebufferFunctions? {
            val entryPoints = when {
                capabilities.OpenGL30 -> Lwjgl3FramebufferEntryPoints.Core
                capabilities.GL_ARB_framebuffer_object -> Lwjgl3FramebufferEntryPoints.ARB
                capabilities.GL_EXT_framebuffer_object -> Lwjgl3FramebufferEntryPoints.EXT
                else -> return null
            }
            return Lwjgl3FramebufferFunctions(entryPoints)
        }
    }
}
