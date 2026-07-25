/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.input

import heckerpowered.bridge.input.*
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

object ForgeKeyBindings {
    private val registry = ForgeKeyBindingRegistry(ClientRegistry::registerKeyBinding)

    fun registerAll() {
        registry.registerAll(KeyBindingRegistry.all())
    }

    fun dispatchPressedBindings() {
        for ((blueprint, nativeBinding) in registry.all()) {
            while (nativeBinding.isPressed) {
                ClientInput.handle(KeyBindingPressedEvent(blueprint.identifier))
            }
        }
    }
}

internal class ForgeKeyBindingRegistry(
    private val registerNativeBinding: (KeyBinding) -> Unit,
) {
    private val registrations = LinkedHashMap<String, RegisteredKeyBinding>()

    fun registerAll(blueprints: Iterable<KeyBindingBlueprint>) {
        for (blueprint in blueprints) {
            register(blueprint)
        }
    }

    fun all(): Collection<RegisteredKeyBinding> {
        return registrations.values
    }

    private fun register(blueprint: KeyBindingBlueprint) {
        val identifier = blueprint.identifier.asString()
        if (identifier in registrations) return

        val nativeBinding = KeyBinding(
            blueprint.translationKey,
            KeyConflictContext.IN_GAME,
            blueprint.defaultKey.toForgeKeyCode(),
            blueprint.categoryTranslationKey,
        )
        registerNativeBinding(nativeBinding)
        registrations[identifier] = RegisteredKeyBinding(blueprint, nativeBinding)
    }
}

internal data class RegisteredKeyBinding(
    val blueprint: KeyBindingBlueprint,
    val nativeBinding: KeyBinding,
)

internal fun KeyboardKey.toForgeKeyCode(): Int {
    return when (this) {
        KeyboardKey.C -> Keyboard.KEY_C
        KeyboardKey.V -> Keyboard.KEY_V
        KeyboardKey.X -> Keyboard.KEY_X
    }
}
