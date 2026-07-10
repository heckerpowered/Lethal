/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.lethal.bridge.adapter.entity.PlayerAccess
import heckerpowered.lethal.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.lethal.bridge.time.Frequency

interface Gun {
    fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency
    fun mayFire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean

    /**
     * Attempts one complete fire transaction.
     *
     * @return `true` only when the transaction completed and should count as a fired operation.
     */
    fun fire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean

    fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess)
}
