/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess

object ZeusBlood : Zeus("zeus_blood", 18_000_000.0, chainRadiusBlocks = 5.0, chainMode = ZeusChainMode.FullCoverage, maximumEnergyPoints = 2_400.0) {
    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 96.0
    }
}
