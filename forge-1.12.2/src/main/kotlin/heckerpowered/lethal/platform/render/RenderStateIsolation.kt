/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import net.minecraft.client.renderer.OpenGlHelper
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

internal object RenderStateIsolation {
    fun isolate(operation: () -> Unit) {
        val snapshot = RenderStateSnapshot.capture()
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            operation()
        } finally {
            GL11.glPopAttrib()
            snapshot.restoreBindings()
        }
    }
}

internal data class RenderStateSnapshot(
    private val framebufferObject: Int,
    private val renderbufferObject: Int,
    private val shaderProgram: Int,
    private val activeTexture: Int,
    private val texture: Int,
) {
    companion object {
        fun capture(): RenderStateSnapshot {
            val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            val texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            GL13.glActiveTexture(activeTexture)

            return RenderStateSnapshot(
                framebufferObject = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                renderbufferObject = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING),
                shaderProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                activeTexture = activeTexture,
                texture = texture,
            )
        }
    }

    fun restoreBindings() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebufferObject)
        OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, renderbufferObject)
        GL20.glUseProgram(shaderProgram)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL13.glActiveTexture(activeTexture)
    }
}
