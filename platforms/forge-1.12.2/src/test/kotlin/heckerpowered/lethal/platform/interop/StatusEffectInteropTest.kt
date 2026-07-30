/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.effect.StatusEffectInstance
import heckerpowered.bridge.adapter.effect.VanillaStatusEffect
import net.minecraft.init.Bootstrap
import net.minecraft.init.MobEffects
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StatusEffectInteropTest {
    @Test
    fun convertsTheGlowingEffectToItsNativeDescription() {
        Bootstrap.register()
        val nativeEffect = StatusEffectInstance(VanillaStatusEffect.Glowing, 1_200, 2).asHost()

        assertSame(MobEffects.GLOWING, nativeEffect.potion)
        assertEquals(expected = 1_200, actual = nativeEffect.duration)
        assertEquals(expected = 2, actual = nativeEffect.amplifier)
    }
}
