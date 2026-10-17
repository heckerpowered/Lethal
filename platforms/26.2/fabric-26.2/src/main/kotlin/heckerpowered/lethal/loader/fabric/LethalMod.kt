/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.fabric

import heckerpowered.lethal.platform.MinecraftBootstrap
import heckerpowered.lethal.platform.MinecraftGameplay
import heckerpowered.lethal.platform.MinecraftRegistrations
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.network.MinecraftNetwork
import heckerpowered.lethal.platform.network.MinecraftPayload
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries

class LethalMod : ModInitializer {
    override fun onInitialize() {
        MinecraftBootstrap.initialize()

        for (item in MinecraftRegistrations.items()) {
            Registry.register(BuiltInRegistries.ITEM, item.blueprint.identifier.asHost(), item)
        }
        for ([identifier, type] in MinecraftRegistrations.entities()) {
            Registry.register(BuiltInRegistries.ENTITY_TYPE, identifier, type)
        }
        for ([identifier, sound] in MinecraftRegistrations.sounds()) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, identifier, sound)
        }
        for ([identifier, tab] in MinecraftRegistrations.creativeTabs()) {
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, identifier, tab)
        }

        PayloadTypeRegistry.serverboundPlay().register(MinecraftPayload.ServerboundType, MinecraftPayload.ServerboundCodec)
        PayloadTypeRegistry.clientboundPlay().register(MinecraftPayload.ClientboundType, MinecraftPayload.ClientboundCodec)
        check(ServerPlayNetworking.registerGlobalReceiver(MinecraftPayload.ServerboundType) { payload, context ->
            MinecraftNetwork.handleServerbound(payload, context.player())
        }) { "Lethal serverbound payload receiver is already registered" }

        ServerTickEvents.END_SERVER_TICK.register { server -> MinecraftGameplay.onServerTick(server) }
        ServerPlayConnectionEvents.DISCONNECT.register { listener, _ ->
            MinecraftGameplay.onPlayerDisconnected(listener.player)
        }
    }
}
