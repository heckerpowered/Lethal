/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.sound.SoundCategory
import net.minecraft.sounds.SoundSource

fun SoundCategory.asHost(): SoundSource {
    return when (this) {
        SoundCategory.Master -> SoundSource.MASTER
        SoundCategory.Music -> SoundSource.MUSIC
        SoundCategory.Records -> SoundSource.RECORDS
        SoundCategory.Weather -> SoundSource.WEATHER
        SoundCategory.Blocks -> SoundSource.BLOCKS
        SoundCategory.Hostile -> SoundSource.HOSTILE
        SoundCategory.Neutral -> SoundSource.NEUTRAL
        SoundCategory.Players -> SoundSource.PLAYERS
        SoundCategory.Ambient -> SoundSource.AMBIENT
        SoundCategory.Voice -> SoundSource.VOICE
    }
}

fun SoundSource.asView(): SoundCategory {
    return when (this) {
        SoundSource.MASTER -> SoundCategory.Master
        SoundSource.MUSIC -> SoundCategory.Music
        SoundSource.RECORDS -> SoundCategory.Records
        SoundSource.WEATHER -> SoundCategory.Weather
        SoundSource.BLOCKS -> SoundCategory.Blocks
        SoundSource.HOSTILE -> SoundCategory.Hostile
        SoundSource.NEUTRAL -> SoundCategory.Neutral
        SoundSource.PLAYERS -> SoundCategory.Players
        SoundSource.AMBIENT -> SoundCategory.Ambient
        SoundSource.VOICE -> SoundCategory.Voice
    }
}
