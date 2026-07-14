/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.sound

import heckerpowered.bridge.adapter.sound.SoundCategory
import heckerpowered.bridge.adapter.sound.SoundEventSpec
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.sound.SoundRegistry
import heckerpowered.lethal.Constants

object ModSounds {
    val FortuneFire = SoundPlayback(SoundEventSpec(Constants.identifier("fortune_fire")), SoundCategory.Players)
    val ArchaeopteryxFire = SoundPlayback(SoundEventSpec(Constants.identifier("archaeopteryx_fire")), SoundCategory.Players)
    val ZeusFire = SoundPlayback(SoundEventSpec(Constants.identifier("zeus_fire")), SoundCategory.Players)

    fun register() {
        SoundRegistry.register(FortuneFire.sound)
        SoundRegistry.register(ArchaeopteryxFire.sound)
        SoundRegistry.register(ZeusFire.sound)
    }
}
