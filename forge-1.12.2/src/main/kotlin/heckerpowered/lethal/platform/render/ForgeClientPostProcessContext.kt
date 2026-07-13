/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.bridge.adapter.client.render.ClientPostProcessContext
import heckerpowered.lethal.platform.render.post.BloomEffect
import net.minecraft.client.Minecraft

internal object ForgeClientPostProcessContext : ClientPostProcessContext {
    override val isBloomSupported: Boolean
        get() = BloomEffect.isSupported

    override fun applyBloomToScene(brightnessThreshold: Float) {
        require(brightnessThreshold.isFinite()) { "Bloom brightness threshold must be finite" }
        val framebuffer = Minecraft.getMinecraft().framebuffer
        val source = ExternalColorRenderTarget(
            framebufferObject = framebuffer.framebufferObject,
            colorTexture = framebuffer.framebufferTexture,
            width = framebuffer.framebufferWidth,
            height = framebuffer.framebufferHeight,
        )
        BloomEffect.apply(source, RenderSurface(source.framebufferObject, source.width, source.height), brightnessThreshold)
    }

    override fun renderContentWithBloom(brightnessThreshold: Float, renderContent: () -> Unit) {
        require(brightnessThreshold.isFinite()) { "Bloom brightness threshold must be finite" }
        val minecraft = Minecraft.getMinecraft()
        val framebuffer = minecraft.framebuffer
        val target = RenderSurface(framebuffer.framebufferObject, framebuffer.framebufferWidth, framebuffer.framebufferHeight)
        if (!BloomEffect.renderContent(target, brightnessThreshold, renderContent)) {
            renderContent()
        }
    }
}
