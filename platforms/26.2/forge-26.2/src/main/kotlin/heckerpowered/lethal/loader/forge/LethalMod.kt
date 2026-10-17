/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.forge

import heckerpowered.lethal.Constants
import heckerpowered.lethal.platform.MinecraftBootstrap
import heckerpowered.lethal.platform.MinecraftGameplay
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.RegisterEvent

@Mod(Constants.MOD_ID)
class LethalMod(context: FMLJavaModLoadingContext) {
    init {
        MinecraftBootstrap.initialize()
        RegisterEvent.getBus(context.modBusGroup).addListener(ForgeRegistrations::register)
        ForgePayloadTransport.initialize()
        TickEvent.ServerTickEvent.Post.BUS.addListener { event -> MinecraftGameplay.onServerTick(event.server()) }
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener { event ->
            val player = event.entity as? ServerPlayer ?: return@addListener
            MinecraftGameplay.onPlayerDisconnected(player)
        }
    }
}
