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

        assertEquals(expected = 1, actual = effect.count)
        assertEquals(expected = Vectors.Zero, actual = effect.positionSpread)
        assertEquals(expected = 0.0, actual = effect.velocitySpread)
        assertFalse(effect.longDistance)
        assertEquals(expected = emptyList(), actual = effect.data)
    }

    @Test
    fun countMustBePositive() {
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.Flame, 0) }
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.Flame, -1) }
    }

    @Test
    fun spreadsMustBeFiniteAndNonNegative() {
        assertFailsWith<IllegalArgumentException> {
            ParticleEffect(VanillaParticle.Flame, 1, Geometry.vector(-1.0, 0.0, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            ParticleEffect(VanillaParticle.Flame, 1, Geometry.vector(0.0, Double.NaN, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            ParticleEffect(VanillaParticle.Flame, 1, Vectors.Zero, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun dataCountMustMatchVanillaParticle() {
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.BlockCrack) }
        assertFailsWith<IllegalArgumentException> { ParticleEffect(VanillaParticle.Flame, 1, Vectors.Zero, 0.0, false, listOf(1)) }

        val effect = ParticleEffect(VanillaParticle.BlockCrack, 1, Vectors.Zero, 0.0, false, listOf(1))
        assertEquals(expected = listOf(1), actual = effect.data)
    }

    @Test
    fun particleDataIsCopied() {
        val data = mutableListOf(1)
        val effect = ParticleEffect(VanillaParticle.BlockDust, 1, Vectors.Zero, 0.0, false, data)

        data[0] = 2

        assertEquals(expected = listOf(1), actual = effect.data)
    }
}
