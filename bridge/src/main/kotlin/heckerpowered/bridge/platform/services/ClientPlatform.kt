/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.platform.services

import heckerpowered.bridge.adapter.client.MinecraftAccess

/**
 * Client-only platform capabilities.
 */
interface ClientPlatform {
    /**
     * Access to the running game client.
     */
    val minecraft: MinecraftAccess
}
