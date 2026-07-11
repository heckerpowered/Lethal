/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.network.PayloadTypeRegistry
import heckerpowered.bridge.network.ServerPlayNetworking

object ModServerPlayNetworking {
    fun onInitialize() {
        PayloadTypeRegistry.ServerboundPlay.register(FireStatePayload.Type, FireStatePayload.Codec)

        ServerPlayNetworking.registerReceiver(FireStatePayload.Type, FireStatePayload::handle)
    }
}