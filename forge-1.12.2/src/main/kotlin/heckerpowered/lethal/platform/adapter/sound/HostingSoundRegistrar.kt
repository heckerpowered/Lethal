/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.sound

import heckerpowered.bridge.adapter.sound.SoundEventSpec
import heckerpowered.bridge.adapter.sound.SoundRegistrar
import heckerpowered.lethal.platform.interop.identifier
import net.minecraft.util.SoundEvent
import net.minecraftforge.fml.common.registry.GameRegistry

class HostingSoundRegistrar : SoundRegistrar {
    override fun register(sound: SoundEventSpec) {
        val identifier = sound.identifier.identifier()
        val soundEvent = SoundEvent(identifier).setRegistryName(identifier)
        val registry = GameRegistry.findRegistry(SoundEvent::class.java)
        registry.register(soundEvent)
    }
}
