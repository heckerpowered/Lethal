/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

object ExperienceOrbAccessImpl {
    @JvmStatic
    fun experiencePoints(value: Int, count: Int): Int {
        return Math.multiplyExact(value, count)
    }
}
