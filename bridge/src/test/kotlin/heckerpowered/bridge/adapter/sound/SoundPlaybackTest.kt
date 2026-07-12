/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.sound

import heckerpowered.bridge.resources.Identifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoundPlaybackTest {
    private val sound = SoundEventSpec(Identifier.create("test", "sound_playback"))

    @Test
    fun defaultsUseNeutralParameters() {
        val playback = SoundPlayback(sound, SoundCategory.Players)

        assertEquals(1.0, playback.volume)
        assertEquals(1.0, playback.pitch)
    }

    @Test
    fun volumeMustBeFiniteAndNonNegative() {
        assertFailsWith<IllegalArgumentException> { SoundPlayback(sound, SoundCategory.Players, volume = -1.0) }
        assertFailsWith<IllegalArgumentException> { SoundPlayback(sound, SoundCategory.Players, volume = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SoundPlayback(sound, SoundCategory.Players, volume = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun pitchMustBeFiniteAndPositive() {
        assertFailsWith<IllegalArgumentException> { SoundPlayback(sound, SoundCategory.Players, pitch = 0.0) }
        assertFailsWith<IllegalArgumentException> { SoundPlayback(sound, SoundCategory.Players, pitch = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SoundPlayback(sound, SoundCategory.Players, pitch = Double.POSITIVE_INFINITY) }
    }
}
