/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.event

import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.lethal.gameplay.common.item.Archaeopteryx
import heckerpowered.lethal.gameplay.common.item.Fortune
import heckerpowered.lethal.gameplay.common.item.Zeus
import heckerpowered.lethal.gameplay.common.item.ZeusBlack
import heckerpowered.lethal.gameplay.common.item.ZeusGlowSquid
import heckerpowered.lethal.gameplay.common.item.ZeusGolden
import heckerpowered.lethal.gameplay.common.item.ZeusSculk
import heckerpowered.lethal.platform.interop.ItemInterop
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.item.Item
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod.EventBusSubscriber
object ModelRegistrationEventHandler {
    @SubscribeEvent
    @JvmStatic
    fun onModelRegistry(event: ModelRegistryEvent) {
        registerRegularItemModel(ItemInterop.item(ItemRegistry[Archaeopteryx]!!))
        registerRegularItemModel(ItemInterop.item(ItemRegistry[Fortune]!!))
        registerRegularItemModel(ItemInterop.item(ItemRegistry[Zeus]!!))
        registerRegularItemModel(ItemInterop.item(ItemRegistry[ZeusGolden]!!))
        registerRegularItemModel(ItemInterop.item(ItemRegistry[ZeusBlack]!!))
        registerRegularItemModel(ItemInterop.item(ItemRegistry[ZeusGlowSquid]!!))
        registerRegularItemModel(ItemInterop.item(ItemRegistry[ZeusSculk]!!))
    }

    private fun registerRegularItemModel(item: Item) {
        ModelLoader.setCustomModelResourceLocation(
            item, 0, ModelResourceLocation(item.registryName, "inventory")
        )
    }
}
