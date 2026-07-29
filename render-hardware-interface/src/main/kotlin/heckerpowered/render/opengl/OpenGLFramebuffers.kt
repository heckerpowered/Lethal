/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import org.lwjgl.opengl.*

internal class OpenGLFramebuffers(framebufferAPI: OpenGLFramebufferAPI?) {
    private val implementation = when (framebufferAPI) {
        OpenGLFramebufferAPI.Core -> CoreOpenGLFramebuffers
        OpenGLFramebufferAPI.ARB -> ARBOpenGLFramebuffers
        OpenGLFramebufferAPI.EXT -> EXTOpenGLFramebuffers
        null -> null
    }

    fun createFramebuffer(): Int = requireImplementation().createFramebuffer()

    fun createRenderbuffer(): Int = requireImplementation().createRenderbuffer()

    fun bindFramebuffer(identifier: Int) = requireImplementation().bindFramebuffer(identifier)

    fun bindRenderbuffer(identifier: Int) = requireImplementation().bindRenderbuffer(identifier)

    fun deleteFramebuffer(identifier: Int) = requireImplementation().deleteFramebuffer(identifier)

    fun deleteRenderbuffer(identifier: Int) = requireImplementation().deleteRenderbuffer(identifier)

    fun allocateDepthStorage(width: Int, height: Int) = requireImplementation().allocateDepthStorage(width, height)

    fun attachColorTexture(textureIdentifier: Int) = requireImplementation().attachColorTexture(textureIdentifier)

    fun attachDepthRenderbuffer(renderbufferIdentifier: Int) = requireImplementation().attachDepthRenderbuffer(renderbufferIdentifier)

    fun status(): Int = requireImplementation().status()

    private fun requireImplementation(): OpenGLFramebufferImplementation {
        return implementation ?: error("OpenGL framebuffer objects are not supported")
    }

    companion object {
        const val FRAMEBUFFER = GL30.GL_FRAMEBUFFER
        const val RENDERBUFFER = GL30.GL_RENDERBUFFER
        const val COLOR_ATTACHMENT = GL30.GL_COLOR_ATTACHMENT0
        const val DEPTH_ATTACHMENT = GL30.GL_DEPTH_ATTACHMENT
        const val COMPLETE = GL30.GL_FRAMEBUFFER_COMPLETE
    }
}

private interface OpenGLFramebufferImplementation {
    fun createFramebuffer(): Int
    fun createRenderbuffer(): Int
    fun bindFramebuffer(identifier: Int)
    fun bindRenderbuffer(identifier: Int)
    fun deleteFramebuffer(identifier: Int)
    fun deleteRenderbuffer(identifier: Int)
    fun allocateDepthStorage(width: Int, height: Int)
    fun attachColorTexture(textureIdentifier: Int)
    fun attachDepthRenderbuffer(renderbufferIdentifier: Int)
    fun status(): Int
}

private object CoreOpenGLFramebuffers : OpenGLFramebufferImplementation {
    override fun createFramebuffer(): Int = GL30.glGenFramebuffers()
    override fun createRenderbuffer(): Int = GL30.glGenRenderbuffers()
    override fun bindFramebuffer(identifier: Int) = GL30.glBindFramebuffer(OpenGLFramebuffers.FRAMEBUFFER, identifier)
    override fun bindRenderbuffer(identifier: Int) = GL30.glBindRenderbuffer(OpenGLFramebuffers.RENDERBUFFER, identifier)
    override fun deleteFramebuffer(identifier: Int) = GL30.glDeleteFramebuffers(identifier)
    override fun deleteRenderbuffer(identifier: Int) = GL30.glDeleteRenderbuffers(identifier)
    override fun allocateDepthStorage(width: Int, height: Int) = GL30.glRenderbufferStorage(OpenGLFramebuffers.RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height)
    override fun attachColorTexture(textureIdentifier: Int) = GL30.glFramebufferTexture2D(OpenGLFramebuffers.FRAMEBUFFER, OpenGLFramebuffers.COLOR_ATTACHMENT, GL11.GL_TEXTURE_2D, textureIdentifier, 0)
    override fun attachDepthRenderbuffer(renderbufferIdentifier: Int) = GL30.glFramebufferRenderbuffer(OpenGLFramebuffers.FRAMEBUFFER, OpenGLFramebuffers.DEPTH_ATTACHMENT, OpenGLFramebuffers.RENDERBUFFER, renderbufferIdentifier)
    override fun status(): Int = GL30.glCheckFramebufferStatus(OpenGLFramebuffers.FRAMEBUFFER)
}

private object ARBOpenGLFramebuffers : OpenGLFramebufferImplementation {
    override fun createFramebuffer(): Int = ARBFramebufferObject.glGenFramebuffers()
    override fun createRenderbuffer(): Int = ARBFramebufferObject.glGenRenderbuffers()
    override fun bindFramebuffer(identifier: Int) = ARBFramebufferObject.glBindFramebuffer(OpenGLFramebuffers.FRAMEBUFFER, identifier)
    override fun bindRenderbuffer(identifier: Int) = ARBFramebufferObject.glBindRenderbuffer(OpenGLFramebuffers.RENDERBUFFER, identifier)
    override fun deleteFramebuffer(identifier: Int) = ARBFramebufferObject.glDeleteFramebuffers(identifier)
    override fun deleteRenderbuffer(identifier: Int) = ARBFramebufferObject.glDeleteRenderbuffers(identifier)
    override fun allocateDepthStorage(width: Int, height: Int) = ARBFramebufferObject.glRenderbufferStorage(OpenGLFramebuffers.RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height)
    override fun attachColorTexture(textureIdentifier: Int) = ARBFramebufferObject.glFramebufferTexture2D(OpenGLFramebuffers.FRAMEBUFFER, OpenGLFramebuffers.COLOR_ATTACHMENT, GL11.GL_TEXTURE_2D, textureIdentifier, 0)
    override fun attachDepthRenderbuffer(renderbufferIdentifier: Int) = ARBFramebufferObject.glFramebufferRenderbuffer(OpenGLFramebuffers.FRAMEBUFFER, OpenGLFramebuffers.DEPTH_ATTACHMENT, OpenGLFramebuffers.RENDERBUFFER, renderbufferIdentifier)
    override fun status(): Int = ARBFramebufferObject.glCheckFramebufferStatus(OpenGLFramebuffers.FRAMEBUFFER)
}

private object EXTOpenGLFramebuffers : OpenGLFramebufferImplementation {
    override fun createFramebuffer(): Int = EXTFramebufferObject.glGenFramebuffersEXT()
    override fun createRenderbuffer(): Int = EXTFramebufferObject.glGenRenderbuffersEXT()
    override fun bindFramebuffer(identifier: Int) = EXTFramebufferObject.glBindFramebufferEXT(OpenGLFramebuffers.FRAMEBUFFER, identifier)
    override fun bindRenderbuffer(identifier: Int) = EXTFramebufferObject.glBindRenderbufferEXT(OpenGLFramebuffers.RENDERBUFFER, identifier)
    override fun deleteFramebuffer(identifier: Int) = EXTFramebufferObject.glDeleteFramebuffersEXT(identifier)
    override fun deleteRenderbuffer(identifier: Int) = EXTFramebufferObject.glDeleteRenderbuffersEXT(identifier)
    override fun allocateDepthStorage(width: Int, height: Int) = EXTFramebufferObject.glRenderbufferStorageEXT(OpenGLFramebuffers.RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height)
    override fun attachColorTexture(textureIdentifier: Int) = EXTFramebufferObject.glFramebufferTexture2DEXT(OpenGLFramebuffers.FRAMEBUFFER, OpenGLFramebuffers.COLOR_ATTACHMENT, GL11.GL_TEXTURE_2D, textureIdentifier, 0)
    override fun attachDepthRenderbuffer(renderbufferIdentifier: Int) = EXTFramebufferObject.glFramebufferRenderbufferEXT(OpenGLFramebuffers.FRAMEBUFFER, OpenGLFramebuffers.DEPTH_ATTACHMENT, OpenGLFramebuffers.RENDERBUFFER, renderbufferIdentifier)
    override fun status(): Int = EXTFramebufferObject.glCheckFramebufferStatusEXT(OpenGLFramebuffers.FRAMEBUFFER)
}
