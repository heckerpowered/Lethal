/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.effect

import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.Vectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ParticleEffectTest {
    @Test
    fun defaultsCreateOneStationaryShortDistanceParticle() {
        val effect = ParticleEffect(VanillaParticle.Flame)

        assertEquals(1, effect.count)
        assertEquals(Vectors.Zero, effect.positionSpread)
        assertEquals(0.0, effect.velocitySpread)
        assertFalse(effect.longDistance)
        assertEquals(emptyList(), effect.data)
    }

    @Test
    fun countMustBePositive() {
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.Flame, count = 0) }
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.Flame, count = -1) }
    }

    @Test
    fun spreadsMustBeFiniteAndNonNegative() {
        assertFailsWith<IllegalArgumentException> {
            ParticleEffect(VanillaParticle.Flame, positionSpread = Geometry.vector(-1.0, 0.0, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            ParticleEffect(VanillaParticle.Flame, positionSpread = Geometry.vector(0.0, Double.NaN, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            ParticleEffect(VanillaParticle.Flame, velocitySpread = Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun dataCountMustMatchVanillaParticle() {
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.BlockCrack) }
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.Flame, data = listOf(1)) }

        val effect = ParticleEffect(VanillaParticle.BlockCrack, data = listOf(1))
        assertEquals(listOf(1), effect.data)
    }

    @Test
    fun particleDataIsCopied() {
        val data = mutableListOf(1)
        val effect = ParticleEffect(VanillaParticle.BlockDust, data = data)

        data[0] = 2

        assertEquals(listOf(1), effect.data)
    }
}
