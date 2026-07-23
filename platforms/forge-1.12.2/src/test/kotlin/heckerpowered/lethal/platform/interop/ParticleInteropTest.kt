/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.effect.VanillaParticle
import net.minecraft.util.EnumParticleTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticleInteropTest {
    @Test
    fun vanillaParticlesMapToNativeParticles() {
        val nativeParticles = VanillaParticle.entries.map(ParticleInterop::particle)

        assertEquals(EnumParticleTypes.entries, nativeParticles)
        assertEquals(VanillaParticle.entries.map { it.dataCount }, nativeParticles.map { it.argumentCount })
    }
}
