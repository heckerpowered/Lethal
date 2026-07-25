/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.network

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.network.ClientboundPayload
import heckerpowered.bridge.network.PayloadTransport
import heckerpowered.bridge.network.ServerboundPayload

class RecordingPayloadTransport : PayloadTransport {
    val serverboundPayloads = mutableListOf<ServerboundPayload<*>>()
    val playerPayloads = mutableListOf<ClientboundPayload<*>>()
    val trackingPayloads = mutableListOf<ClientboundPayload<*>>()

    override fun sendToServer(payload: ServerboundPayload<*>) {
        serverboundPayloads += payload
    }

    override fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>) {
        playerPayloads += payload
    }

    override fun sendToPlayersTracking(entity: EntityAccess, payload: ClientboundPayload<*>) {
        trackingPayloads += payload
    }

    fun clear() {
        serverboundPayloads.clear()
        playerPayloads.clear()
        trackingPayloads.clear()
    }
}
