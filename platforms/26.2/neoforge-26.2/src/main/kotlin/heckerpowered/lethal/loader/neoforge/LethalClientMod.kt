/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.neoforge

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
import net.minecraft.client.player.LocalPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent
import net.neoforged.neoforge.common.NeoForge

@Mod(Constants.MOD_ID, dist = [Dist.CLIENT])
class LethalClientMod(modEventBus: IEventBus) {
    init {
        MinecraftClientBootstrap.initialize()
        modEventBus.addListener(::registerRenderers)
        modEventBus.addListener(::registerKeyMappings)
        modEventBus.addListener(::registerPayloadHandlers)
        NeoForge.EVENT_BUS.addListener { _: ClientTickEvent.Post -> MinecraftInput.onClientTick() }
    }

    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(MinecraftRegistrations.entityType(StarJudgement)) { StarJudgementEntityRenderer(it) }
        event.registerEntityRenderer(MinecraftRegistrations.entityType(EnhancedStarJudgement)) { StarJudgementEntityRenderer(it) }
        event.registerEntityRenderer(MinecraftRegistrations.entityType(ZeusMissile)) { ZeusMissileEntityRenderer(it) }
        event.registerEntityRenderer(MinecraftRegistrations.entityType(AthenaGrenade)) { AthenaGrenadeEntityRenderer(it) }
    }

    private fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        MinecraftInput.keyMappings().forEach(event::register)
    }

    private fun registerPayloadHandlers(event: RegisterClientPayloadHandlersEvent) {
        event.register(MinecraftPayload.ClientboundType) { payload, context ->
            MinecraftNetwork.handleClientbound(payload, context.player() as LocalPlayer)
        }
    }
}
