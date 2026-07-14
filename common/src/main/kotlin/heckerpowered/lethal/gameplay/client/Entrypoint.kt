/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.platform.services.ClientEntrypoint
import heckerpowered.lethal.gameplay.client.network.ModClientPlayNetworking
import heckerpowered.lethal.gameplay.client.render.DivineShotEffect

class Entrypoint : ClientEntrypoint {
    override fun onEntrypoint() {
        MouseEventHandler.onInitialize()
        BloomTest.onInitialize()
        DivineShotEffect.onInitialize()
        ModClientPlayNetworking.onInitialize()
    }
}
