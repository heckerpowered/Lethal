/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.creativetab

import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabBlueprint
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.Archaeopteryx
import heckerpowered.lethal.gameplay.common.item.Fortune
import heckerpowered.lethal.gameplay.common.item.Zeus
import heckerpowered.lethal.gameplay.common.item.ZeusBlack
import heckerpowered.lethal.gameplay.common.item.ZeusGlowSquid
import heckerpowered.lethal.gameplay.common.item.ZeusGolden
import heckerpowered.lethal.gameplay.common.item.ZeusSculk

object ModCreativeModeTabs {
    val Lethal = CreativeModeTabBlueprint(
        identifier = Constants.identifier("main"),
        titleTranslationKey = "itemGroup.lethal",
        icon = Fortune,
        items = listOf(Archaeopteryx, Fortune, Zeus, ZeusGolden, ZeusBlack, ZeusGlowSquid, ZeusSculk),
    )

    fun register() {
        CreativeModeTabRegistry.register(Lethal)
    }
}
