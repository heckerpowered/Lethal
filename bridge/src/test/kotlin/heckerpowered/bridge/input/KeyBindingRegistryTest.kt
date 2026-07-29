/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.resources.IdentifierProvider
import kotlin.test.*

class KeyBindingRegistryTest {
    @BeforeTest
    fun setUp() {
        KeyBindingRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        KeyBindingRegistry.clear()
    }

    @Test
    fun registrationPreservesOrderAndAllowsSharedDefaultKeys() {
        val primarySkill = keyBinding("skill/primary", KeyboardKey.X)
        val secondarySkill = keyBinding("skill/secondary", KeyboardKey.X)

        assertSame(primarySkill, KeyBindingRegistry.register(primarySkill))
        assertSame(secondarySkill, KeyBindingRegistry.register(secondarySkill))

        assertEquals(expected = listOf(primarySkill, secondarySkill), actual = KeyBindingRegistry.all())
    }

    @Test
    fun duplicateBindingIdentifiersAreRejected() {
        KeyBindingRegistry.register(keyBinding("skill/primary", KeyboardKey.X))

        assertFailsWith<IllegalArgumentException> {
            KeyBindingRegistry.register(keyBinding("skill/primary", KeyboardKey.C))
        }
    }

    @Test
    fun languageKeysAreDerivedFromIdentifiers() {
        val keyBinding = keyBinding("skill/primary", KeyboardKey.X)

        assertEquals(expected = "key.lethal.skill.primary", actual = keyBinding.translationKey)
        assertEquals(expected = "key.category.lethal.weapon.skills", actual = keyBinding.categoryTranslationKey)
    }

    private fun keyBinding(path: String, defaultKey: KeyboardKey): KeyBindingBlueprint {
        return KeyBindingBlueprint(IdentifierProvider.Freestanding.identifier("lethal", path), IdentifierProvider.Freestanding.identifier("lethal", "weapon/skills"), defaultKey)
    }
}
