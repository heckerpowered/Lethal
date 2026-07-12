/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.sound

import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier

object SoundRegistry {
    private val registrations = mutableMapOf<String, SoundEventSpec>()

    fun register(sound: SoundEventSpec): SoundEventSpec {
        val identifier = sound.identifier.asString()
        require(identifier !in registrations) { "Sound event has already been registered: $identifier" }

        Services.SoundRegistrar.register(sound)
        registrations[identifier] = sound
        return sound
    }

    operator fun get(identifier: Identifier): SoundEventSpec? {
        return registrations[identifier.asString()]
    }
}
