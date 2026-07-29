/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.input

import heckerpowered.bridge.input.KeyBindingBlueprint
import heckerpowered.bridge.input.KeyBindingRegistry
import heckerpowered.bridge.input.KeyboardKey
import heckerpowered.lethal.Constants

object ModKeyBindings {
    private val SkillCategory = Constants.identifier("weapon/skills")

    val PrimarySkill = register("skill/primary", KeyboardKey.X)
    val SecondarySkill = register("skill/secondary", KeyboardKey.C)
    val UltimateSkill = register("skill/ultimate", KeyboardKey.V)

    fun onInitialize() {
    }

    private fun register(path: String, defaultKey: KeyboardKey): KeyBindingBlueprint {
        return KeyBindingRegistry.register(KeyBindingBlueprint(Constants.identifier(path), SkillCategory, defaultKey))
    }
}
