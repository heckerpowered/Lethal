/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.platform.services

import heckerpowered.bridge.adapter.client.MinecraftAccess

/**
 * The client side of the platform bridge. [minecraft] gives client code access to the running
 * game without depending on a particular host version’s client class.
 */
interface ClientPlatform {
    /**
     * Access to the running game client.
     */
    val minecraft: MinecraftAccess
}
