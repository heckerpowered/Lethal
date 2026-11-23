/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.lethal.platform.interop.asView
import heckerpowered.math.VectorView
import net.minecraft.util.DamageSource

/**
 * Exposes the world-space origin of native damage to bridge code when the source supplies one. An
 * unattributed origin is represented by null, not by a zero vector.
 */
object DamageSourceAccessImpl {
    @JvmStatic
    fun position(source: DamageSource): VectorView? = source.damageLocation?.asView()
}
