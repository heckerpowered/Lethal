/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.network

import heckerpowered.bridge.network.codec.StreamCodec

interface PayloadRegistrar {
    companion object {
        fun <T : ClientboundPayload<T>> registerClientbound(type: Payload.Type<T>, codec: StreamCodec<StreamBuffer, T>): PayloadDefinition<T> {
            return PayloadTypeRegistry.Clientbound.register(type, codec)
        }

        fun <T : ServerboundPayload<T>> registerServerbound(type: Payload.Type<T>, codec: StreamCodec<StreamBuffer, T>): PayloadDefinition<T> {
            return PayloadTypeRegistry.Serverbound.register(type, codec)
        }
    }
}