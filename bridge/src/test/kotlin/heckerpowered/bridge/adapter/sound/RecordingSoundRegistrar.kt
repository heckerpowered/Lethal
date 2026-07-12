/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.sound

class RecordingSoundRegistrar : SoundRegistrar {
    val sounds = mutableListOf<SoundEventSpec>()

    override fun register(sound: SoundEventSpec) {
        sounds += sound
    }
}
