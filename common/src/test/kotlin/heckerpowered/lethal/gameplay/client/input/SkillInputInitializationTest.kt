/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.input

import heckerpowered.bridge.input.KeyBindingInputRule
import heckerpowered.bridge.input.KeyBindingRegistry
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.all
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillInputInitializationTest {
    @Test
    fun repeatedInitializationDoesNotRegisterDuplicateBindingsOrRules() {
        ModKeyBindings.onInitialize()
        SkillKeyInputHandler.onInitialize()
        ModKeyBindings.onInitialize()
        SkillKeyInputHandler.onInitialize()

        val bindingIdentifiers = KeyBindingRegistry.all()
            .map { binding -> binding.identifier.asString() }
        assertEquals(1, bindingIdentifiers.count { identifier -> identifier == ModKeyBindings.PrimarySkill.identifier.asString() })
        assertEquals(1, bindingIdentifiers.count { identifier -> identifier == ModKeyBindings.SecondarySkill.identifier.asString() })
        assertEquals(1, bindingIdentifiers.count { identifier -> identifier == ModKeyBindings.UltimateSkill.identifier.asString() })

        val inputRuleRegistrations = RuleRegistry.all<KeyBindingInputRule>()
            .count { rule -> rule === SkillKeyInputHandler }
        assertEquals(1, inputRuleRegistrations)
    }
}
