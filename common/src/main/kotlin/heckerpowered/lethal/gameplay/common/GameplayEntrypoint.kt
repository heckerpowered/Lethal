/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import heckerpowered.bridge.platform.services.Entrypoint
import heckerpowered.lethal.gameplay.common.creativetab.ModCreativeModeTabs
import heckerpowered.lethal.gameplay.common.item.ModItems
import heckerpowered.lethal.gameplay.common.network.ModServerPlayNetworking
import heckerpowered.lethal.gameplay.common.skill.WeaponSkillActivation
import heckerpowered.lethal.gameplay.common.sound.ModSounds

class GameplayEntrypoint : Entrypoint {
    override fun onEntrypoint() {
        ModServerPlayNetworking.onInitialize()
        WeaponSkillActivation.onInitialize()
        ModSounds.onInitialize()
        ModCreativeModeTabs.onInitialize()
        ModItems.onInitialize()
    }
}
