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
    val FortuneFire = register("fortune_fire")
    val FortunePerkUltimate = register("fortune_perk_ultimate")
    val ArchaeopteryxFire = register("archaeopteryx_fire")
    val ChaosFire = register("chaos_fire")
    val ZeusFire = register("zeus_fire")

    fun onInitialize() {
    }

    private fun register(path: String): SoundPlayback {
        val sound = SoundRegistry.register(SoundEventSpec(Constants.identifier(path)))
        return SoundPlayback(sound, SoundCategory.Players)
    }
}
