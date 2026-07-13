/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import net.minecraft.client.renderer.OpenGlHelper
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14

internal enum class Blending {
    Replace,
    Additive,
    AdditiveColor,
    PremultipliedAlpha,
}

internal object PostProcessRenderer {
    private const val SCREEN_QUAD_VERTEX_SHADER = "/assets/lethal/shaders/core/screen_quad.vsh"
    private const val COPY_FRAGMENT_SHADER = "/assets/lethal/shaders/blit/copy.fsh"
    private val CopyProgram by lazy { BlitProgram(SCREEN_QUAD_VERTEX_SHADER, COPY_FRAGMENT_SHADER) }

    fun prepare() {
        CopyProgram
    }

    fun clear(target: ColorRenderTarget) {
        bind(RenderSurface(target.framebufferObject, target.width, target.height))
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F)
        GL11.glClearDepth(1.0)
        GL11.glDepthMask(true)
        GL11.glColorMask(true, true, true, true)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
    }

    fun bind(target: RenderSurface) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target.framebufferObject)
        GL11.glViewport(0, 0, target.width, target.height)
    }

    fun copy(source: ColorRenderTarget, target: RenderSurface, blending: Blending = Blending.Replace) {
        render(CopyProgram, source, target, blending) {}
    }

    fun render(program: BlitProgram, source: ColorRenderTarget, target: RenderSurface, blending: Blending, uploadUniforms: BlitProgram.() -> Unit) {
        require(source.framebufferObject != target.framebufferObject) { "A post-process pass cannot read from its output framebuffer" }

        bind(target)
        preparePipeline(blending)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, source.colorTexture)
        program.bind()
        program.setInteger("framebuffer", 0)
        program.uploadUniforms()
        drawScreenQuad()
    }

    private fun preparePipeline(blending: Blending) {
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(false)
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP)
        GL11.glColorMask(true, true, true, true)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL14.glBlendEquation(GL14.GL_FUNC_ADD)

        when (blending) {
            Blending.Replace -> GL11.glDisable(GL11.GL_BLEND)
            Blending.Additive -> {
                GL11.glEnable(GL11.GL_BLEND)
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE)
            }
            Blending.AdditiveColor -> {
                GL11.glEnable(GL11.GL_BLEND)
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO)
            }
            Blending.PremultipliedAlpha -> {
                GL11.glEnable(GL11.GL_BLEND)
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
            }
        }
    }

    private fun drawScreenQuad() {
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0.0F, 0.0F)
        GL11.glVertex2f(-1.0F, -1.0F)
        GL11.glTexCoord2f(1.0F, 0.0F)
        GL11.glVertex2f(1.0F, -1.0F)
        GL11.glTexCoord2f(1.0F, 1.0F)
        GL11.glVertex2f(1.0F, 1.0F)
        GL11.glTexCoord2f(0.0F, 1.0F)
        GL11.glVertex2f(-1.0F, 1.0F)
        GL11.glEnd()
    }
}
