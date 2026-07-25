/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.resources.Identifier

/**
 * Version-independent declaration of a client key binding.
 *
 * Native key codes, configured keys, conflict handling, and click queues remain
 * owned by the client platform.
 */
data class KeyBindingBlueprint(
    val identifier: Identifier,
    val category: Identifier,
    val defaultKey: KeyboardKey,
) {
    val translationKey: String
        get() = identifier.toLanguageKey("key")

    val categoryTranslationKey: String
        get() = category.toLanguageKey("key.category")
}

enum class KeyboardKey {
    C,
    V,
    X,
}

data class KeyBindingPressedEvent(val bindingIdentifier: Identifier)

interface KeyBindingInputRule {
    fun onKeyBindingInput(event: KeyBindingPressedEvent)
}

private fun Identifier.toLanguageKey(prefix: String): String {
    return "$prefix.$namespace.${path.replace('/', '.')}"
}
