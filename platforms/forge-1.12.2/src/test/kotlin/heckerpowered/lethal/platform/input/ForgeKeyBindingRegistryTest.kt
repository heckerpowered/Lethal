/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.input

import heckerpowered.bridge.input.KeyBindingBlueprint
import heckerpowered.bridge.input.KeyboardKey
import heckerpowered.bridge.resources.IdentifierProvider
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.client.settings.KeyConflictContext
import org.lwjgl.input.Keyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ForgeKeyBindingRegistryTest {
    @Test
    fun repeatedRegistrationDoesNotCreateDuplicateNativeBindings() {
        val nativeBindings = mutableListOf<KeyBinding>()
        val registry = ForgeKeyBindingRegistry(nativeBindings::add)
        val blueprint = keyBinding("skill/primary", KeyboardKey.X)

        registry.registerAll(listOf(blueprint))
        registry.registerAll(listOf(blueprint))

        assertEquals(expected = 1, actual = nativeBindings.size)
        assertEquals(expected = listOf(blueprint), actual = registry.all().map(RegisteredKeyBinding::blueprint))
    }

    @Test
    fun nativeBindingPreservesPortableMetadataAndInGameContext() {
        val nativeBindings = mutableListOf<KeyBinding>()
        val registry = ForgeKeyBindingRegistry(nativeBindings::add)
        val blueprint = keyBinding("skill/secondary", KeyboardKey.C)

        registry.registerAll(listOf(blueprint))

        val nativeBinding = nativeBindings.single()
        assertEquals(expected = blueprint.translationKey, actual = nativeBinding.keyDescription)
        assertEquals(expected = blueprint.categoryTranslationKey, actual = nativeBinding.keyCategory)
        assertEquals(expected = Keyboard.KEY_C, actual = nativeBinding.keyCodeDefault)
        assertSame(KeyConflictContext.IN_GAME, nativeBinding.keyConflictContext)
    }

    private fun keyBinding(path: String, defaultKey: KeyboardKey): KeyBindingBlueprint {
        return KeyBindingBlueprint(IdentifierProvider.Freestanding.identifier("lethal", path), IdentifierProvider.Freestanding.identifier("lethal", "weapon/skills"), defaultKey)
    }
}
