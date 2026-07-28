/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.effect

import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.math.Vectors

class ParticleEffect(val particle: VanillaParticle, val count: Int = 1, val positionSpread: VectorView = Vectors.Zero, val velocitySpread: Double = 0.0, val longDistance: Boolean = false, data: List<Int> = emptyList()) {
    val data = data.toList()

    init {
        require(count > 0) { "Particle count must be positive" }
        require(positionSpread.x.isFinite() && positionSpread.x >= 0.0) { "Particle X spread must be finite and non-negative" }
        require(positionSpread.y.isFinite() && positionSpread.y >= 0.0) { "Particle Y spread must be finite and non-negative" }
        require(positionSpread.z.isFinite() && positionSpread.z >= 0.0) { "Particle Z spread must be finite and non-negative" }
        require(velocitySpread.isFinite() && velocitySpread >= 0.0) { "Particle velocity spread must be finite and non-negative" }
        require(data.size == particle.dataCount) { "Particle data count must be " + particle.dataCount }
    }
}
