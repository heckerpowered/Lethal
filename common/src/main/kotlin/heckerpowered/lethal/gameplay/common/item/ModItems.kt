/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.item.ItemBlueprint
import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.bridge.platform.Services

object ModItems {
    init {
        if (Services.Platform.isDevelopmentEnvironment) {
            register(TestItem)
            register(TestFirearmItem)
        }

        register(Archaeopteryx)
        register(CosmicStarshatterShotgun)
        register(Fortune)
        register(EnhancedFortune)
        register(Chaos)
        register(Zeus)
        register(ZeusGolden)
        register(ZeusBlackGold)
        register(ZeusGlowSquid)
        register(ZeusSculk)
        register(ZeusBlood)
        ZeusArmor.Pieces.forEach(::register)
    }

    fun onInitialize() {
    }

    private fun register(blueprint: ItemBlueprint) {
        ItemRegistry.register(blueprint)
    }
}
