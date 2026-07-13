/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.time.Frequency

interface Gun {
    fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency
    fun mayFire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean

    /**
     * Attempts up to [requestedShotCount] fire transactions.
     *
     * @return the number of completed fire transactions.
     */
    fun fire(player: PlayerAccess, weaponStack: ItemStackAccess, requestedShotCount: Long = 1): Long

    fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long)
}
