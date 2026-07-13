/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageSources
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.Firearm
import heckerpowered.lethal.gameplay.common.network.DivineParticlePayload
import heckerpowered.lethal.gameplay.common.sound.ModSounds

object Divine : Firearm() {
    override val identifier: Identifier
        get() = Constants.identifier("divine")

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perSecond(2)
    }

    override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        player.world.playSound(player.eyePosition, ModSounds.DivineFire)

        val damageSource = DamageSources.vanilla(VanillaDamageType.FellOutOfWorld, player, player)
        val damagePoints = 200000.0 * shotCount.toDouble()
        player.world.raycastEntityHits(Geometry.ray(player.eyePosition, player.viewVector), 100.0, player)
            .forEach {
                it.entity.hurt(damageSource, damagePoints)
            }
        Services.PayloadTransport.sendToPlayer(player as? ServerPlayerAccess ?: return, DivineParticlePayload)
    }
}
