/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.creativetab

import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.*

object ModCreativeModeTabs {
    val Lethal = register(
        CreativeModeTabBlueprint(
            Constants.identifier("main"), "itemGroup.lethal", Fortune,
            listOf(
                Archaeopteryx,
                CosmicStarshatterShotgun,
                Fortune,
                EnhancedFortune,
                Chaos,
                Zeus,
                ZeusGolden,
                ZeusBlackGold,
                ZeusGlowSquid,
                ZeusSculk,
                ZeusArmor.Helmet,
                ZeusArmor.Chestplate,
                ZeusArmor.Leggings,
                ZeusArmor.Boots,
            ),
        ),
    )

    fun onInitialize() {
    }

    private fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabBlueprint {
        return CreativeModeTabRegistry.register(blueprint)
    }
}
