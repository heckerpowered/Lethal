/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.event

import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.adapter.item.HostedItem
import net.minecraft.item.Item
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
object ItemRegistrationEventHandler {
    @SubscribeEvent
    @JvmStatic
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        for (blueprint in ItemRegistry.all()) {
            event.registry.register(HostedItem(blueprint))
        }
    }
}
