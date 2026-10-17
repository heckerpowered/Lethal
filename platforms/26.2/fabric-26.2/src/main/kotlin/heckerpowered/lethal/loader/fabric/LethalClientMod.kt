/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.loader.fabric

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
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.renderer.entity.EntityRenderers

class LethalClientMod : ClientModInitializer {
    override fun onInitializeClient() {
        MinecraftClientBootstrap.initialize()
        EntityRenderers.register(MinecraftRegistrations.entityType(StarJudgement)) { StarJudgementEntityRenderer(it) }
        EntityRenderers.register(MinecraftRegistrations.entityType(EnhancedStarJudgement)) { StarJudgementEntityRenderer(it) }
        EntityRenderers.register(MinecraftRegistrations.entityType(ZeusMissile)) { ZeusMissileEntityRenderer(it) }
        EntityRenderers.register(MinecraftRegistrations.entityType(AthenaGrenade)) { AthenaGrenadeEntityRenderer(it) }
        MinecraftInput.keyMappings().forEach(KeyMappingHelper::registerKeyMapping)

        check(ClientPlayNetworking.registerGlobalReceiver(MinecraftPayload.ClientboundType) { payload, context ->
            MinecraftNetwork.handleClientbound(payload, context.player())
        }) { "Lethal clientbound payload receiver is already registered" }

        ClientTickEvents.END_CLIENT_TICK.register { MinecraftInput.onClientTick() }
    }
}
