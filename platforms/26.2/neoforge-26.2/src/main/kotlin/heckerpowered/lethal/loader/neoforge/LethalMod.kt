/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.neoforge

import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.MinecraftBootstrap
import heckerpowered.lethal.platform.MinecraftGameplay
import heckerpowered.lethal.platform.MinecraftRegistrations
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.network.MinecraftNetwork
import heckerpowered.lethal.platform.network.MinecraftPayload
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.HandlerThread
import net.neoforged.neoforge.registries.RegisterEvent

@Mod(Constants.MOD_ID)
class LethalMod(modEventBus: IEventBus) {
    init {
        MinecraftBootstrap.initialize()
        modEventBus.addListener(::registerContent)
        modEventBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post -> MinecraftGameplay.onServerTick(event.server) }
        NeoForge.EVENT_BUS.addListener(::onPlayerDisconnected)
    }

    private fun registerContent(event: RegisterEvent) {
        event.register(Registries.ITEM) { registry ->
            for (item in MinecraftRegistrations.items()) {
                registry.register(item.blueprint.identifier.asHost(), item)
            }
        }
        event.register(Registries.ENTITY_TYPE) { registry ->
            for ([identifier, type] in MinecraftRegistrations.entities()) {
                registry.register(identifier, type)
            }
        }
        event.register(Registries.CREATIVE_MODE_TAB) { registry ->
            for ([identifier, tab] in MinecraftRegistrations.creativeTabs()) {
                registry.register(identifier, tab)
            }
        }
        event.register(Registries.SOUND_EVENT) { registry ->
            for ([identifier, sound] in MinecraftRegistrations.sounds()) {
                registry.register(identifier, sound)
            }
        }
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1").executesOn(HandlerThread.MAIN)
        registrar.playToServer(MinecraftPayload.ServerboundType, MinecraftPayload.ServerboundCodec) { payload, context ->
            MinecraftNetwork.handleServerbound(payload, context.player() as ServerPlayer)
        }
        registrar.playToClient(MinecraftPayload.ClientboundType, MinecraftPayload.ClientboundCodec)
    }

    private fun onPlayerDisconnected(event: PlayerEvent.PlayerLoggedOutEvent) {
        MinecraftGameplay.onPlayerDisconnected(event.entity as ServerPlayer)
    }
}
