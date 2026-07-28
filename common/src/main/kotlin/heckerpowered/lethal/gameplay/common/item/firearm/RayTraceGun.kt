/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.adapter.world.raycast.raycastEntityHits
import heckerpowered.bridge.math.Geometry
import heckerpowered.bridge.math.RayView

abstract class RayTraceGun : Firearm() {
    protected abstract fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double

    final override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        val ray = Geometry.ray(player.eyePosition, player.viewVector)
        val distanceBlocks = getRayTraceDistanceBlocks(player, weaponStack)
        val entityHits = player.world.raycastEntityHits(ray, distanceBlocks, player)
        val unobstructedEntityHits = filterUnobstructedEntityHits(player, weaponStack, ray, entityHits)
        onRayTrace(player, weaponStack, shotCount, unobstructedEntityHits)
    }

    private fun filterUnobstructedEntityHits(player: PlayerAccess, weaponStack: ItemStackAccess, ray: RayView, entityHits: Sequence<EntityRayHit>): Sequence<EntityRayHit> {
        if (canRayPassThroughAllBlocks(player, weaponStack)) return entityHits
        return entityHits.takeUntilBlocked(player.world, ray) { isRayBlockedBy(player, weaponStack, it) }
    }

    /**
     * Returns whether this shot ignores every block collision.
     *
     * When this returns true, no block raycast is performed.
     */
    protected open fun canRayPassThroughAllBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
        return false
    }

    /**
     * Returns whether [blockHit] stops this gun's ray.
     *
     * Override this for selective penetration. Use [canRayPassThroughAllBlocks] when every block is ignored so
     * the block raycast can be skipped entirely.
     */
    protected open fun isRayBlockedBy(player: PlayerAccess, weaponStack: ItemStackAccess, blockHit: BlockHitResult): Boolean {
        return true
    }

    /**
     * Handles the ordered entity hits that are visible from the firing position.
     * [entityHits] is single-use so consuming it cannot repeat world queries.
     */
    protected abstract fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>)
}

private fun Sequence<EntityRayHit>.takeUntilBlocked(world: WorldAccess, ray: RayView, isBlocking: (BlockHitResult) -> Boolean): Sequence<EntityRayHit> {
    return sequence {
        var segmentStart = ray.origin

        for (entityHit in this@takeUntilBlocked) {
            val segmentDistanceBlocks = segmentStart.distanceTo(entityHit.point)
            val segmentRay = Geometry.ray(segmentStart, ray.direction)
            if (world.raycastBlockHits(segmentRay, segmentDistanceBlocks).any(isBlocking)) break

            yield(entityHit)
            segmentStart = entityHit.point
        }
    }.constrainOnce()
}
