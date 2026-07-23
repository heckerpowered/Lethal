/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.sound.SoundCategory
import net.minecraft.util.SoundCategory as NativeSoundCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class SoundInteropTest {
    @Test
    fun soundCategoriesMapToNativeCategories() {
        val expectedCategories = listOf(
            NativeSoundCategory.MASTER,
            NativeSoundCategory.MUSIC,
            NativeSoundCategory.RECORDS,
            NativeSoundCategory.WEATHER,
            NativeSoundCategory.BLOCKS,
            NativeSoundCategory.HOSTILE,
            NativeSoundCategory.NEUTRAL,
            NativeSoundCategory.PLAYERS,
            NativeSoundCategory.AMBIENT,
            NativeSoundCategory.VOICE,
        )

        assertEquals(expectedCategories, SoundCategory.entries.map(SoundInterop::soundCategory))
    }
}
