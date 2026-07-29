/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client

import heckerpowered.bridge.adapter.BridgeAccess
import heckerpowered.bridge.adapter.entity.ClientPlayerAccess

interface MinecraftAccess : BridgeAccess {
    val player: ClientPlayerAccess?
}
