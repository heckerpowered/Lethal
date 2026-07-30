/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.bridge.adapter.client.render.ClientWorldPostProcessRule
import heckerpowered.bridge.adapter.client.render.ClientWorldRenderRule
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.forEach
import heckerpowered.lethal.Constants
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
object ClientWorldRenderPipeline {
    @SubscribeEvent
    @JvmStatic
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val context = MinecraftClientWorldRenderContext(event.partialTicks)
        val graphicsDevice = MinecraftGraphics.Device
        val renderManager = Minecraft.getMinecraft().renderManager
        val cameraPosition = Geometry.vector(renderManager.viewerPosX, renderManager.viewerPosY, renderManager.viewerPosZ)
        val modelViewProjectionMatrix = graphicsDevice.currentModelViewProjection()
        val target = MinecraftGraphics.mainRenderTarget()

        graphicsDevice.encode("World effects") {
            val encoder = MinecraftClientWorldRenderEncoder(this, graphicsDevice, target, cameraPosition, modelViewProjectionMatrix)
            RuleRegistry.forEach<ClientWorldRenderRule> { rule -> with(rule) { encoder.render(context) } }
            RuleRegistry.forEach<ClientWorldPostProcessRule> { rule -> with(rule) { encoder.postProcess(context) } }
        }
    }
}
