/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common

import heckerpowered.bridge.adapter.item.ItemRegistry
import heckerpowered.bridge.adapter.item.creativetab.CreativeModeTabRegistry
import heckerpowered.bridge.adapter.sound.SoundRegistry
import heckerpowered.lethal.gameplay.common.creativetab.ModCreativeModeTabs
import heckerpowered.lethal.gameplay.common.item.Archaeopteryx
import heckerpowered.lethal.gameplay.common.item.Chaos
import heckerpowered.lethal.gameplay.common.item.EnhancedFortune
import heckerpowered.lethal.gameplay.common.item.Fortune
import heckerpowered.lethal.gameplay.common.item.Zeus
import heckerpowered.lethal.gameplay.common.item.ZeusBlackGold
import heckerpowered.lethal.gameplay.common.item.ZeusGlowSquid
import heckerpowered.lethal.gameplay.common.item.ZeusGolden
import heckerpowered.lethal.gameplay.common.item.ZeusSculk
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import kotlin.test.Test
import kotlin.test.assertEquals

class GameplayContentInitializationTest {
    @Test
    fun repeatedEntrypointCallsDoNotRegisterDuplicateDefinitions() {
        val entrypoint = GameplayEntrypoint()

        entrypoint.onEntrypoint()
        entrypoint.onEntrypoint()

        assertEquals(
            expected = listOf(
                ModSounds.FortuneFire.sound,
                ModSounds.FortunePerkUltimate.sound,
                ModSounds.ArchaeopteryxFire.sound,
                ModSounds.ChaosFire.sound,
                ModSounds.ZeusFire.sound,
            ),
            actual = SoundRegistry.all(),
        )
        assertEquals(expected = listOf(ModCreativeModeTabs.Lethal), actual = CreativeModeTabRegistry.all())
        assertEquals(
            expected = listOf(
                Archaeopteryx,
                Fortune,
                EnhancedFortune,
                Chaos,
                Zeus,
                ZeusGolden,
                ZeusBlackGold,
                ZeusGlowSquid,
                ZeusSculk,
            ),
            actual = ItemRegistry.all(),
        )
    }
}
