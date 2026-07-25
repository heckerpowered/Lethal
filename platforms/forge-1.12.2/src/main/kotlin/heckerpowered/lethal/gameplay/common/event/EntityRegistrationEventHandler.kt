/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.event

import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.adapter.entity.ForgeEntityTypes
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.registry.EntityEntry

@Mod.EventBusSubscriber(modid = Constants.MOD_ID)
class EntityRegistrationEventHandler private constructor() {
    companion object {
        @SubscribeEvent
        @JvmStatic
        fun onRegisterEntities(event: RegistryEvent.Register<EntityEntry>) {
            for (entityType in ForgeEntityTypes.all()) {
                event.registry.register(entityType)
            }
        }
    }
}
