/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.forge

import heckerpowered.lethal.platform.MinecraftRegistrations
import heckerpowered.lethal.platform.interop.asHost
import net.minecraft.core.registries.Registries
import net.minecraftforge.registries.RegisterEvent

internal object ForgeRegistrations {
    fun register(event: RegisterEvent) {
        event.register(Registries.ITEM) { registry ->
            MinecraftRegistrations.items().forEach { item ->
                registry.register(item.blueprint.identifier.asHost(), item)
            }
        }
        event.register(Registries.ENTITY_TYPE) { registry ->
            MinecraftRegistrations.entities().forEach { [identifier, type] ->
                registry.register(identifier, type)
            }
        }
        event.register(Registries.SOUND_EVENT) { registry ->
            MinecraftRegistrations.sounds().forEach { [identifier, sound] ->
                registry.register(identifier, sound)
            }
        }
        event.register(Registries.CREATIVE_MODE_TAB) { registry ->
            MinecraftRegistrations.creativeTabs().forEach { [identifier, tab] ->
                registry.register(identifier, tab)
            }
        }
    }
}
