/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

object ZeusBlackGold : Zeus("zeus_black_gold", 12_000_000.0, chainRadiusBlocks = 4.0, maximumEnergyPoints = 2_400.0) {
    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 80.0
    }
}
