/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess

interface PayloadTransport {
    fun sendToServer(payload: ServerboundPayload<*>)
    fun sendToPlayer(player: ServerPlayerAccess, payload: ClientboundPayload<*>)

    /**
     * Sends [payload] to players tracking [entity].
     *
     * When [entity] is a server player, that player is included.
     */
    fun sendToPlayersTracking(entity: EntityAccess, payload: ClientboundPayload<*>)
}
