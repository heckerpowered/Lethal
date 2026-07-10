/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.network

import heckerpowered.lethal.bridge.resources.Identifier

interface Payload<T : Payload<T>> {
    val type: Type<T>

    data class Type<T : Payload<T>>(val id: Identifier) {
        override fun toString(): String {
            return id.asString()
        }
    }
}

interface ClientboundPayload<T : ClientboundPayload<T>> : Payload<T>
interface ServerboundPayload<T : ServerboundPayload<T>> : Payload<T>

