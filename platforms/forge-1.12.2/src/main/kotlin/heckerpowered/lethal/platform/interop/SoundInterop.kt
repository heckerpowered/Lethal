/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.sound.SoundCategory
import heckerpowered.bridge.adapter.sound.SoundEventSpec
import net.minecraft.util.SoundCategory as NativeSoundCategory
import net.minecraft.util.SoundEvent as NativeSoundEvent

object SoundInterop {
    @JvmStatic
    fun soundEvent(sound: SoundEventSpec): NativeSoundEvent {
        val identifier = sound.identifier.identifier()
        return NativeSoundEvent.REGISTRY.getObject(identifier)
            ?: error("Unregistered sound event: " + sound.identifier.asString())
    }

    @JvmStatic
    fun soundCategory(category: SoundCategory): NativeSoundCategory {
        return when (category) {
            SoundCategory.Master -> NativeSoundCategory.MASTER
            SoundCategory.Music -> NativeSoundCategory.MUSIC
            SoundCategory.Records -> NativeSoundCategory.RECORDS
            SoundCategory.Weather -> NativeSoundCategory.WEATHER
            SoundCategory.Blocks -> NativeSoundCategory.BLOCKS
            SoundCategory.Hostile -> NativeSoundCategory.HOSTILE
            SoundCategory.Neutral -> NativeSoundCategory.NEUTRAL
            SoundCategory.Players -> NativeSoundCategory.PLAYERS
            SoundCategory.Ambient -> NativeSoundCategory.AMBIENT
            SoundCategory.Voice -> NativeSoundCategory.VOICE
        }
    }
}

fun SoundEventSpec.soundEvent(): NativeSoundEvent {
    return SoundInterop.soundEvent(this)
}

fun SoundCategory.soundCategory(): NativeSoundCategory {
    return SoundInterop.soundCategory(this)
}
