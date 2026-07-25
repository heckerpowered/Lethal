/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.resources.IdentifierProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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

        assertEquals(listOf(primarySkill, secondarySkill), KeyBindingRegistry.all())
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

        assertEquals("key.lethal.skill.primary", keyBinding.translationKey)
        assertEquals("key.category.lethal.weapon.skills", keyBinding.categoryTranslationKey)
    }

    private fun keyBinding(path: String, defaultKey: KeyboardKey): KeyBindingBlueprint {
        return KeyBindingBlueprint(
            identifier = IdentifierProvider.Freestanding.identifier("lethal", path),
            category = IdentifierProvider.Freestanding.identifier("lethal", "weapon/skills"),
            defaultKey = defaultKey,
        )
    }
}
