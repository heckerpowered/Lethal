/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.RayTraceGun
import heckerpowered.lethal.gameplay.common.sound.ModSounds

object Archaeopteryx : RayTraceGun() {
    private val hitDamage = EntityHitDamage(VanillaDamageType.Generic, 7.0)

    override val identifier: Identifier
        get() = Constants.identifier("archaeopteryx")

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perMinute(650)
    }

    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 120.0
    }

    override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        player.world.playSound(player.eyePosition, ModSounds.ArchaeopteryxFire)
        hitDamage.apply(player, weaponStack, shotCount, entityHits)
    }
}
