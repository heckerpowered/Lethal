/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.platform.services.ClientEntrypoint
import heckerpowered.lethal.gameplay.client.input.ModKeyBindings
import heckerpowered.lethal.gameplay.client.input.SkillKeyInputHandler
import heckerpowered.lethal.gameplay.client.network.ModClientPlayNetworking
import heckerpowered.lethal.gameplay.client.render.postprocess.bloom.BloomEffect
import heckerpowered.lethal.gameplay.client.render.zeus.ZeusElectricLineEffect

class Entrypoint : ClientEntrypoint {
    override fun onEntrypoint() {
        initializeInput()
        initializeRendering()
        initializeNetworking()
    }

    private fun initializeInput() {
        ModKeyBindings.onInitialize()
        SkillKeyInputHandler.onInitialize()
        MouseEventHandler.onInitialize()
    }

    private fun initializeRendering() {
        BloomTest.onInitialize()
        BloomEffect.onInitialize()
        ZeusElectricLineEffect.onInitialize()
    }

    private fun initializeNetworking() {
        ModClientPlayNetworking.onInitialize()
    }
}
