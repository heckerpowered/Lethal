/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.input

import heckerpowered.bridge.input.KeyBindingPressedEvent
import heckerpowered.bridge.input.KeyboardKey
import heckerpowered.bridge.resources.IdentifierProvider
import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillKeyInputTest {
    @Test
    fun skillBindingsUseTheLegacyDefaultKeys() {
        assertEquals(KeyboardKey.X, ModKeyBindings.PrimarySkill.defaultKey)
        assertEquals(KeyboardKey.C, ModKeyBindings.SecondarySkill.defaultKey)
        assertEquals(KeyboardKey.V, ModKeyBindings.UltimateSkill.defaultKey)
    }

    @Test
    fun registeredSkillBindingsMapToTheirServerAuthoritativeSlots() {
        val activatedSlots = mutableListOf<SkillSlot>()
        val input = SkillKeyInput(hasPlayer = { true }, sendActivation = activatedSlots::add)

        input.onKeyBindingInput(KeyBindingPressedEvent(ModKeyBindings.PrimarySkill.identifier))
        input.onKeyBindingInput(KeyBindingPressedEvent(ModKeyBindings.SecondarySkill.identifier))
        input.onKeyBindingInput(KeyBindingPressedEvent(ModKeyBindings.UltimateSkill.identifier))

        assertEquals(
            listOf(SkillSlot.Primary, SkillSlot.Secondary, SkillSlot.Ultimate),
            activatedSlots,
        )
    }

    @Test
    fun inputIsNotSentWithoutAClientPlayer() {
        val activatedSlots = mutableListOf<SkillSlot>()
        val input = SkillKeyInput(hasPlayer = { false }, sendActivation = activatedSlots::add)

        input.onKeyBindingInput(KeyBindingPressedEvent(ModKeyBindings.PrimarySkill.identifier))

        assertEquals(emptyList(), activatedSlots)
    }

    @Test
    fun unrelatedBindingsAreIgnored() {
        val activatedSlots = mutableListOf<SkillSlot>()
        val input = SkillKeyInput(hasPlayer = { true }, sendActivation = activatedSlots::add)
        val unrelatedBinding = IdentifierProvider.Freestanding.identifier("other", "skill/primary")

        input.onKeyBindingInput(KeyBindingPressedEvent(unrelatedBinding))

        assertEquals(emptyList(), activatedSlots)
    }
}
