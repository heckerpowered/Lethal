/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.platform.services.ClientEntrypoint
import heckerpowered.lethal.gameplay.client.input.ModKeyBindings
import heckerpowered.lethal.gameplay.client.input.SkillKeyInputHandler
import heckerpowered.lethal.gameplay.client.network.ModClientPlayNetworking
import heckerpowered.lethal.gameplay.client.render.BloomEffect
import heckerpowered.lethal.gameplay.client.render.ZeusChainEffect
import heckerpowered.lethal.gameplay.client.render.ZeusShotEffect

class Entrypoint : ClientEntrypoint {
    override fun onEntrypoint() {
        ModKeyBindings.onInitialize()
        SkillKeyInputHandler.onInitialize()
        MouseEventHandler.onInitialize()
        BloomTest.onInitialize()
        BloomEffect.onInitialize()
        ZeusChainEffect.onInitialize()
        ZeusShotEffect.onInitialize()
        ModClientPlayNetworking.onInitialize()
    }
}
