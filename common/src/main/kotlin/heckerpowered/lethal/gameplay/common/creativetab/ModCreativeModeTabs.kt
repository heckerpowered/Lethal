/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.creativetab

import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.Archaeopteryx
import heckerpowered.lethal.gameplay.common.item.Chaos
import heckerpowered.lethal.gameplay.common.item.EnhancedFortune
import heckerpowered.lethal.gameplay.common.item.Fortune
import heckerpowered.lethal.gameplay.common.item.Zeus
import heckerpowered.lethal.gameplay.common.item.ZeusBlackGold
import heckerpowered.lethal.gameplay.common.item.ZeusGlowSquid
import heckerpowered.lethal.gameplay.common.item.ZeusGolden
import heckerpowered.lethal.gameplay.common.item.ZeusSculk

object ModCreativeModeTabs {
    val Lethal = register(
        CreativeModeTabBlueprint(
            Constants.identifier("main"), "itemGroup.lethal", Fortune,
            listOf(
                Archaeopteryx,
                Fortune,
                EnhancedFortune,
                Chaos,
                Zeus,
                ZeusGolden,
                ZeusBlackGold,
                ZeusGlowSquid,
                ZeusSculk,
            ),
        ),
    )

    fun onInitialize() {
    }

    private fun register(blueprint: CreativeModeTabBlueprint): CreativeModeTabBlueprint {
        return CreativeModeTabRegistry.register(blueprint)
    }
}
