/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.sound

import heckerpowered.bridge.resources.Identifier

object SoundRegistry {
    private val Registry = SoundEventRegistry()

    fun register(sound: SoundEventSpec): SoundEventSpec = Registry.register(sound)

    operator fun get(identifier: Identifier): SoundEventSpec? = Registry[identifier]

    fun all(): List<SoundEventSpec> = Registry.all()
}

internal class SoundEventRegistry {
    private val soundsByIdentifier = LinkedHashMap<String, SoundEventSpec>()

    fun register(sound: SoundEventSpec): SoundEventSpec {
        val identifier = sound.identifier.asString()
        require(identifier !in soundsByIdentifier) { "Sound event has already been registered: $identifier" }

        soundsByIdentifier[identifier] = sound
        return sound
    }

    operator fun get(identifier: Identifier): SoundEventSpec? {
        return soundsByIdentifier[identifier.asString()]
    }

    fun all(): List<SoundEventSpec> {
        return soundsByIdentifier.values.toList()
    }
}
