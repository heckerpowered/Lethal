/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.sound

data class SoundPlayback(
    val sound: SoundEventSpec,
    val category: SoundCategory,
    val volume: Double = 1.0,
    val pitch: Double = 1.0,
) {
    init {
        require(volume.isFinite() && volume >= 0.0) { "Volume must be finite and non-negative" }
        require(pitch.isFinite() && pitch > 0.0) { "Pitch must be finite and positive" }
    }
}
