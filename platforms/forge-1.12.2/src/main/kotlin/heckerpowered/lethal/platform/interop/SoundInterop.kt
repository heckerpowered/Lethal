/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.sound.SoundCategory
import heckerpowered.bridge.adapter.sound.SoundEventSpec
import net.minecraft.util.SoundCategory as NativeSoundCategory
import net.minecraft.util.SoundEvent as NativeSoundEvent

fun SoundEventSpec.asHost(): NativeSoundEvent {
    return NativeSoundEvent.REGISTRY.getObject(identifier.asHost()) ?: error("Unregistered sound event: ${identifier.asString()}")
}

fun SoundCategory.asHost(): NativeSoundCategory {
    return when (this) {
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
