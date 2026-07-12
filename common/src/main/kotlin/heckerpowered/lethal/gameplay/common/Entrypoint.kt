/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.services.Entrypoint
import heckerpowered.lethal.gameplay.common.item.Archaeopteryx
import heckerpowered.lethal.gameplay.common.item.Fortune
import heckerpowered.lethal.gameplay.common.item.TestFirearmItem
import heckerpowered.lethal.gameplay.common.item.TestItem
import heckerpowered.lethal.gameplay.common.network.ModServerPlayNetworking
import heckerpowered.lethal.gameplay.common.sound.ModSounds

class Entrypoint : Entrypoint {
    override fun onEntrypoint() {
        ModServerPlayNetworking.onInitialize()
        ModSounds.register()
        registerItem()
    }

    private fun registerItem() {
        if (Services.Platform.isDevelopmentEnvironment) {
            ItemRegistry.register(TestItem)
            ItemRegistry.register(TestFirearmItem)
        }

        ItemRegistry.register(Archaeopteryx)
        ItemRegistry.register(Fortune)
    }
}
