/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.bridge.adapter.client.render.ClientPostProcessRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.forEach
import heckerpowered.lethal.Constants
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
class ClientPostProcessPipeline private constructor() {
    companion object {
        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        @JvmStatic
        fun onRenderGameOverlay(event: RenderGameOverlayEvent.Pre) {
            if (event.type != RenderGameOverlayEvent.ElementType.ALL) return
            RuleRegistry.forEach<ClientPostProcessRule> { rule -> rule.onPostProcess(ForgeClientPostProcessContext) }
        }
    }
}
