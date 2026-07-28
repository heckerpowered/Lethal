/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.bridge.adapter.client.render.ClientWorldRenderRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.forEach
import heckerpowered.lethal.Constants
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
object ClientWorldRenderPipeline {
    @SubscribeEvent
    @JvmStatic
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val context = ForgeClientWorldRenderContext(event.partialTicks)
        ForgeClientWorldRenderFrame().use { frame ->
            RuleRegistry.forEach<ClientWorldRenderRule> { rule -> rule.onWorldRender(context, frame) }
        }
    }
}
