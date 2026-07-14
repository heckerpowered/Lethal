/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderContext
import heckerpowered.bridge.adapter.client.render.RenderColor
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RotatorView
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.platform.interop.ObjectInterop
import heckerpowered.lethal.platform.render.post.BloomEffect
import net.minecraft.client.Minecraft

internal class ForgeClientWorldRenderContext(override val partialTick: Float) : ClientWorldRenderContext {
    override val isBloomSupported: Boolean
        get() = BloomEffect.isSupported

    override fun interpolateEyePosition(entity: EntityAccess): VectorView {
        val minecraftEntity = ObjectInterop.entity(entity)
        return Geometry.vector(
            interpolate(minecraftEntity.lastTickPosX, minecraftEntity.posX),
            interpolate(minecraftEntity.lastTickPosY, minecraftEntity.posY) + minecraftEntity.eyeHeight,
            interpolate(minecraftEntity.lastTickPosZ, minecraftEntity.posZ),
        )
    }

    override fun interpolateRotation(entity: EntityAccess): RotatorView {
        val minecraftEntity = ObjectInterop.entity(entity)
        val pitchDegrees = interpolate(minecraftEntity.prevRotationPitch.toDouble(), minecraftEntity.rotationPitch.toDouble())
        val yawDegrees = interpolate(minecraftEntity.prevRotationYaw.toDouble(), minecraftEntity.rotationYaw.toDouble())
        return Geometry.rotator(pitchDegrees, yawDegrees)
    }

    override fun drawLine(startPosition: VectorView, endPosition: VectorView, widthPixels: Float, color: RenderColor, lightningIntensity: Float, lightningSpikeDensity: Float, lightningAnimationFrequency: Float) {
        WorldLineRenderer.drawLine(startPosition, endPosition, widthPixels, color, lightningIntensity, lightningSpikeDensity, lightningAnimationFrequency)
    }

    override fun renderContentWithBloom(brightnessThreshold: Float, renderContent: () -> Unit) {
        require(brightnessThreshold.isFinite()) { "Bloom brightness threshold must be finite" }
        val framebuffer = Minecraft.getMinecraft().framebuffer
        val target = RenderSurface(framebuffer.framebufferObject, framebuffer.framebufferWidth, framebuffer.framebufferHeight)
        if (!BloomEffect.renderWorldContent(target, framebuffer.depthBuffer, brightnessThreshold, renderContent)) {
            renderContent()
        }
    }

    private fun interpolate(previousValue: Double, currentValue: Double): Double {
        return previousValue + (currentValue - previousValue) * partialTick.toDouble()
    }
}
