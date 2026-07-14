/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.math.Geometry

abstract class RayTraceGun : Firearm() {
    protected abstract fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double

    final override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        val ray = Geometry.ray(player.eyePosition, player.viewVector)
        val distanceBlocks = getRayTraceDistanceBlocks(player, weaponStack)
        val entityHits = player.world.raycastEntityHits(ray, distanceBlocks, player)
        onRayTrace(player, weaponStack, shotCount, entityHits)
    }

    protected abstract fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>)
}
