/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.adapter.entity.ServerPlayerAccess

interface PayloadTransport {
    fun sendToServer(payload: ServerboundPayload<*>)
    fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>)
}