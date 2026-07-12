/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.sound

import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SoundRegistryTest {
    @Test
    fun registerDelegatesToPlatformAndStoresSound() {
        val identifier = Identifier.create("test", "sound_registry_delegation")
        val sound = SoundEventSpec(identifier)

        val registeredSound = SoundRegistry.register(sound)

        assertSame(sound, registeredSound)
        assertSame(sound, SoundRegistry[identifier])
        assertEquals(sound, (Services.SoundRegistrar as RecordingSoundRegistrar).sounds.last())
    }

    @Test
    fun registerRejectsDuplicateIdentifier() {
        val identifier = Identifier.create("test", "sound_registry_duplicate")
        SoundRegistry.register(SoundEventSpec(identifier))

        assertFailsWith<IllegalArgumentException> {
            SoundRegistry.register(SoundEventSpec(identifier))
        }
    }
}
