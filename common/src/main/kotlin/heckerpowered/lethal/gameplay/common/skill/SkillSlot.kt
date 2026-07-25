/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

enum class SkillSlot(internal val networkId: Byte) {
    Primary(0),
    Secondary(1),
    Ultimate(2);

    companion object {
        internal fun fromNetworkId(networkId: Byte): SkillSlot {
            return when (networkId.toInt()) {
                0 -> Primary
                1 -> Secondary
                2 -> Ultimate
                else -> throw IllegalArgumentException("Unknown skill slot: $networkId")
            }
        }
    }
}
