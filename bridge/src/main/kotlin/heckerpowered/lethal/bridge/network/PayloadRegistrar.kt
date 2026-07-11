/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.network

import heckerpowered.lethal.bridge.network.codec.StreamCodec

interface PayloadRegistrar {
    fun <T : ClientboundPayload<T>> registerClientbound(type: Payload.Type<T>, codec: StreamCodec<StreamBuffer, T>): PayloadDefinition<T>

    fun <T : ServerboundPayload<T>> registerServerbound(type: Payload.Type<T>, codec: StreamCodec<StreamBuffer, T>): PayloadDefinition<T>
}
