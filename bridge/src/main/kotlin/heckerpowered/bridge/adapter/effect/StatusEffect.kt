/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.effect

enum class VanillaStatusEffect {
    Glowing,
}

data class StatusEffectInstance(
    val type: VanillaStatusEffect,
    val durationTicks: Int,
    val amplifier: Int = 0,
)
