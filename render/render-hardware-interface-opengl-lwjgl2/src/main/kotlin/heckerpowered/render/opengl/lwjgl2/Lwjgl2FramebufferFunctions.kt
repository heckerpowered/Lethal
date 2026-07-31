/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.opengl.FramebufferName
import heckerpowered.render.opengl.RenderbufferName
import heckerpowered.render.opengl.TextureName
import heckerpowered.render.opengl.function.OpenGLFramebufferFunctions
import org.lwjgl.opengl.*

internal class Lwjgl2FramebufferFunctions private constructor(
    private val entryPoints: Lwjgl2FramebufferEntryPoints,
) : OpenGLFramebufferFunctions {
    private val framebufferTarget = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_FRAMEBUFFER
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_FRAMEBUFFER
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_EXT
    }
    private val drawFramebufferTarget = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_DRAW_FRAMEBUFFER
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_DRAW_FRAMEBUFFER
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_EXT
    }
    private val readFramebufferTarget = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_READ_FRAMEBUFFER
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_READ_FRAMEBUFFER
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_EXT
    }
    private val drawFramebufferBinding = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_DRAW_FRAMEBUFFER_BINDING
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
    }
    private val readFramebufferBinding = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_READ_FRAMEBUFFER_BINDING
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_READ_FRAMEBUFFER_BINDING
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
    }
    private val renderbufferTarget = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_RENDERBUFFER
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_RENDERBUFFER
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_RENDERBUFFER_EXT
    }
    private val renderbufferBinding = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.GL_RENDERBUFFER_BINDING
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.GL_RENDERBUFFER_BINDING
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.GL_RENDERBUFFER_BINDING_EXT
    }

    override fun createFramebuffer(): FramebufferName {
        val name = when (entryPoints) {
            Lwjgl2FramebufferEntryPoints.Core -> GL30.glGenFramebuffers()
            Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glGenFramebuffers()
            Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glGenFramebuffersEXT()
        }
        return FramebufferName(name)
    }

    override fun getBoundDrawFramebuffer(): FramebufferName = FramebufferName(GL11.glGetInteger(drawFramebufferBinding))

    override fun getBoundReadFramebuffer(): FramebufferName = FramebufferName(GL11.glGetInteger(readFramebufferBinding))

    override fun bindDrawFramebuffer(framebuffer: FramebufferName) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glBindFramebuffer(drawFramebufferTarget, framebuffer.value)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glBindFramebuffer(drawFramebufferTarget, framebuffer.value)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glBindFramebufferEXT(drawFramebufferTarget, framebuffer.value)
    }

    override fun bindReadFramebuffer(framebuffer: FramebufferName) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glBindFramebuffer(readFramebufferTarget, framebuffer.value)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glBindFramebuffer(readFramebufferTarget, framebuffer.value)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glBindFramebufferEXT(readFramebufferTarget, framebuffer.value)
    }

    override fun framebufferTexture2D(attachment: Int, texture: TextureName, level: Int) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glFramebufferTexture2D(framebufferTarget, attachment, GL11.GL_TEXTURE_2D, texture.value, level)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glFramebufferTexture2D(framebufferTarget, attachment, GL11.GL_TEXTURE_2D, texture.value, level)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glFramebufferTexture2DEXT(framebufferTarget, attachment, GL11.GL_TEXTURE_2D, texture.value, level)
    }

    override fun checkFramebufferStatus(): Int = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glCheckFramebufferStatus(framebufferTarget)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glCheckFramebufferStatus(framebufferTarget)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glCheckFramebufferStatusEXT(framebufferTarget)
    }

    override fun deleteFramebuffer(framebuffer: FramebufferName) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glDeleteFramebuffers(framebuffer.value)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glDeleteFramebuffers(framebuffer.value)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer.value)
    }

    override fun createRenderbuffer(): RenderbufferName {
        val name = when (entryPoints) {
            Lwjgl2FramebufferEntryPoints.Core -> GL30.glGenRenderbuffers()
            Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glGenRenderbuffers()
            Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glGenRenderbuffersEXT()
        }
        return RenderbufferName(name)
    }

    override fun getBoundRenderbuffer(): RenderbufferName = RenderbufferName(GL11.glGetInteger(renderbufferBinding))

    override fun bindRenderbuffer(renderbuffer: RenderbufferName) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glBindRenderbuffer(renderbufferTarget, renderbuffer.value)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glBindRenderbuffer(renderbufferTarget, renderbuffer.value)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glBindRenderbufferEXT(renderbufferTarget, renderbuffer.value)
    }

    override fun renderbufferStorage(internalFormat: Int, width: Int, height: Int) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glRenderbufferStorage(renderbufferTarget, internalFormat, width, height)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glRenderbufferStorage(renderbufferTarget, internalFormat, width, height)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glRenderbufferStorageEXT(renderbufferTarget, internalFormat, width, height)
    }

    override fun framebufferRenderbuffer(attachment: Int, renderbuffer: RenderbufferName) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glFramebufferRenderbuffer(framebufferTarget, attachment, renderbufferTarget, renderbuffer.value)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glFramebufferRenderbuffer(framebufferTarget, attachment, renderbufferTarget, renderbuffer.value)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glFramebufferRenderbufferEXT(framebufferTarget, attachment, renderbufferTarget, renderbuffer.value)
    }

    override fun deleteRenderbuffer(renderbuffer: RenderbufferName) = when (entryPoints) {
        Lwjgl2FramebufferEntryPoints.Core -> GL30.glDeleteRenderbuffers(renderbuffer.value)
        Lwjgl2FramebufferEntryPoints.ARB -> ARBFramebufferObject.glDeleteRenderbuffers(renderbuffer.value)
        Lwjgl2FramebufferEntryPoints.EXT -> EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer.value)
    }

    companion object {
        fun create(capabilities: ContextCapabilities): OpenGLFramebufferFunctions? {
            val entryPoints = when {
                capabilities.OpenGL30 -> Lwjgl2FramebufferEntryPoints.Core
                capabilities.GL_ARB_framebuffer_object -> Lwjgl2FramebufferEntryPoints.ARB
                capabilities.GL_EXT_framebuffer_object -> Lwjgl2FramebufferEntryPoints.EXT
                else -> return null
            }
            return Lwjgl2FramebufferFunctions(entryPoints)
        }
    }
}
