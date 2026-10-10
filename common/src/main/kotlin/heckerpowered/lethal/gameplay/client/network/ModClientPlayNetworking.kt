/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.network

import heckerpowered.bridge.network.ClientPlayNetworking
import heckerpowered.lethal.gameplay.common.network.*

object ModClientPlayNetworking {
    init {
        ClientPlayNetworking.registerReceiver(ZeusChainPayload.Type, ZeusChainPayload::handle)
        ClientPlayNetworking.registerReceiver(ZeusShotPayload.Type, ZeusShotPayload::handle)
        ClientPlayNetworking.registerReceiver(ZeusJudgementPayload.Type, ZeusJudgementPayload::handle)
        ClientPlayNetworking.registerReceiver(ZeusMissileExplosionPayload.Type, ZeusMissileExplosionPayload::handle)
        ClientPlayNetworking.registerReceiver(AthenaGrenadeExplosionPayload.Type, AthenaGrenadeExplosionPayload::handle)
    }

    fun onInitialize() {
    }
}
