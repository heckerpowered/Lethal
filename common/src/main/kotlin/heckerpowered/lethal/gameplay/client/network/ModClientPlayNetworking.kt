/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.network

import heckerpowered.bridge.network.ClientPlayNetworking
import heckerpowered.lethal.gameplay.common.network.ZeusChainPayload
import heckerpowered.lethal.gameplay.common.network.ZeusShotPayload

object ModClientPlayNetworking {
    init {
        ClientPlayNetworking.registerReceiver(ZeusChainPayload.Type, ZeusChainPayload::handle)
        ClientPlayNetworking.registerReceiver(ZeusShotPayload.Type, ZeusShotPayload::handle)
    }

    fun onInitialize() {
    }
}
