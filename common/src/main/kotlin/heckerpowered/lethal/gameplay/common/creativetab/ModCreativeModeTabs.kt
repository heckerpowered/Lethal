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

object ModCreativeModeTabs {
    val Lethal = CreativeModeTabBlueprint(
        identifier = Constants.identifier("main"),
        titleTranslationKey = "itemGroup.lethal",
        icon = Fortune,
        items = listOf(Archaeopteryx, Fortune),
    )

    fun register() {
        CreativeModeTabRegistry.register(Lethal)
    }
}
