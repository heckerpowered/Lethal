/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.event

import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.item.Item
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
object ModelRegistrationEventHandler {
    @SubscribeEvent
    @JvmStatic
    fun onModelRegistry(event: ModelRegistryEvent) {
        for (blueprint in ItemRegistry.all()) {
            registerRegularItemModel(blueprint.asHost())
        }
    }

    private fun registerRegularItemModel(item: Item) {
        ModelLoader.setCustomModelResourceLocation(item, 0, ModelResourceLocation(item.registryName, "inventory"))
    }
}
