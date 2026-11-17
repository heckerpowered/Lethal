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
import heckerpowered.math.Ray
import heckerpowered.math.RayView

abstract class RayTraceGun : Firearm() {
    protected abstract fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double

    protected fun traceRay(player: PlayerAccess, weaponStack: ItemStackAccess, ray: RayView, distanceBlocks: Double): Sequence<EntityRayHit> {
        val entityHits = player.world.raycastEntityHits(ray, distanceBlocks, player)
        return filterUnobstructedEntityHits(player, weaponStack, ray, entityHits)
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
}

private fun Sequence<EntityRayHit>.takeUntilBlocked(world: WorldAccess, ray: RayView, isBlocking: (BlockHitResult) -> Boolean): Sequence<EntityRayHit> {
    return sequence {
        var segmentStart = ray.origin

        for (entityHit in this@takeUntilBlocked) {
            val segmentDistanceBlocks = segmentStart.distanceTo(entityHit.point)
            val segmentRay = Ray(segmentStart, ray.direction)
            if (world.raycastBlockHits(segmentRay, segmentDistanceBlocks).any(isBlocking)) break

            yield(entityHit)
            segmentStart = entityHit.point
        }
    }.constrainOnce()
}