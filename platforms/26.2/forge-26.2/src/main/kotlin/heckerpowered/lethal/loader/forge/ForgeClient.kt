/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.forge

import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.entity.athena.AthenaGrenade
import heckerpowered.lethal.gameplay.common.entity.starjudgement.EnhancedStarJudgement
import heckerpowered.lethal.gameplay.common.entity.starjudgement.StarJudgement
import heckerpowered.lethal.gameplay.common.entity.zeus.ZeusMissile
import heckerpowered.lethal.platform.MinecraftClientBootstrap
import heckerpowered.lethal.platform.MinecraftInput
import heckerpowered.lethal.platform.MinecraftRegistrations
import heckerpowered.lethal.platform.network.MinecraftNetwork
import heckerpowered.lethal.platform.network.MinecraftPayload
import heckerpowered.lethal.platform.render.entity.AthenaGrenadeEntityRenderer
import heckerpowered.lethal.platform.render.entity.StarJudgementEntityRenderer
import heckerpowered.lethal.platform.render.entity.ZeusMissileEntityRenderer
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.listener.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.Channel

@Mod.EventBusSubscriber(
    modid = Constants.MOD_ID,
    value = [Dist.CLIENT],
    bus = Mod.EventBusSubscriber.Bus.FORGE,
)
internal object ForgeClient {
    @JvmStatic
    @SubscribeEvent
    fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(MinecraftRegistrations.entityType(StarJudgement)) { StarJudgementEntityRenderer(it) }
        event.registerEntityRenderer(MinecraftRegistrations.entityType(EnhancedStarJudgement)) { StarJudgementEntityRenderer(it) }
        event.registerEntityRenderer(MinecraftRegistrations.entityType(ZeusMissile)) { ZeusMissileEntityRenderer(it) }
        event.registerEntityRenderer(MinecraftRegistrations.entityType(AthenaGrenade)) { AthenaGrenadeEntityRenderer(it) }
    }

    @JvmStatic
    @SubscribeEvent
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        // Forge registers keys before FMLClientSetupEvent during Minecraft construction.
        MinecraftClientBootstrap.initialize()
        MinecraftInput.keyMappings().forEach(event::register)
    }

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent.Post) {
        MinecraftInput.onClientTick()
    }

    fun handlePayload(payload: MinecraftPayload) {
        val player = Minecraft.getInstance().player ?: return
        MinecraftNetwork.handleClientbound(payload, player)
    }

    fun sendToServer(payload: MinecraftPayload, channel: Channel<CustomPacketPayload>) {
        val connection = checkNotNull(Minecraft.getInstance().connection) { "Lethal requires a play connection to send a serverbound payload" }
        channel.send(payload, connection.connection)
    }
}
