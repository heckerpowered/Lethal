/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.network

import heckerpowered.bridge.network.ClientPlayNetworking
import heckerpowered.lethal.gameplay.common.network.DivineParticlePayload

object ModClientPlayNetworking {
    fun onInitialize() {
        ClientPlayNetworking.registerReceiver(DivineParticlePayload.Type, DivineParticlePayload::handle)
    }
}