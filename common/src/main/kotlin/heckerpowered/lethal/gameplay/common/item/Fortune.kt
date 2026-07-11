/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm

object Fortune : Firearm() {
    override val identifier: Identifier
        get() = Constants.identifier("fortune")

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perMinute(840)
    }

    override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess) {
        val damageSource = DamageSources.vanilla(VanillaDamageType.FellOutOfWorld, player, player)
        player.world.raycastEntityHits(Geometry.ray(player.eyePosition, player.viewVector), 100.0, player)
            .forEach {
                it.entity.hurt(damageSource, 30000.0)
            }
    }
}