/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.RayTraceGun
import heckerpowered.lethal.gameplay.common.network.ZeusShotPayload
import heckerpowered.lethal.gameplay.common.sound.ModSounds

open class Zeus protected constructor(identifierPath: String, damagePointsPerShot: Double) : RayTraceGun() {
    companion object : Zeus("zeus", 100_000.0)

    private val hitDamage = EntityHitDamage(VanillaDamageType.FellOutOfWorld, damagePointsPerShot)

    final override val identifier: Identifier = Constants.identifier(identifierPath)

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
        return Frequency.perSecond(2)
    }

    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double {
        return 64.0
    }

    override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        player.world.playSound(player.eyePosition, ModSounds.ZeusFire)
        hitDamage.apply(player, shotCount, entityHits)

        val isMainHand = player.getEquippedStack(EquipmentSlot.MainHand) === weaponStack
        val serverPlayer = player as? ServerPlayerAccess ?: return
        Services.PayloadTransport.sendToPlayer(serverPlayer, ZeusShotPayload(isMainHand))
    }
}
