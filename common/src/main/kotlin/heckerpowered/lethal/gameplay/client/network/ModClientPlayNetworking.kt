/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.network

import heckerpowered.bridge.network.ClientPlayNetworking
import heckerpowered.lethal.gameplay.common.network.ZeusShotPayload

object ModClientPlayNetworking {
    fun onInitialize() {
        ClientPlayNetworking.registerReceiver(ZeusShotPayload.Type, ZeusShotPayload::handle)
    }
}
