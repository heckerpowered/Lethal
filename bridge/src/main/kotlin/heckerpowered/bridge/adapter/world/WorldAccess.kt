/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world

import heckerpowered.bridge.adapter.BridgeAccess
import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.raycast.BlockHitResult
import heckerpowered.bridge.adapter.world.raycast.BlockRaycastShape
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucketSource
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.RayView
import heckerpowered.bridge.math.VectorView

/**
 * Host-neutral world access.
 */
interface WorldAccess : BridgeAccess, EntityRayBucketSource {
    val isClientSide: Boolean

    /**
     * Number of loaded entities visible to this world access.
     *
     * Hosts should expose this as a cheap count instead of deriving it by consuming [entities].
     */
    val loadedEntityCount: Int

    /**
     * Loaded entities visible to this world access.
     */
    val entities: Sequence<EntityAccess>

    /**
     * Returns entities whose bounding boxes intersect [searchBox].
     *
     * Implementations should preserve lazy traversal when the host exposes spatial entity storage.
     */
    fun getEntities(searchBox: BoxView): Sequence<EntityAccess>

    /**
     * Returns block hits in ascending ray time using [shape].
     *
     * [distanceBlocks] is the physical length of the ray segment and must be finite and non-negative.
     * A direction without magnitude produces an empty sequence. Liquids are ignored. [BlockRaycastShape.Collision]
     * also ignores blocks without native collision boxes, except for host-defined special cases such as portals.
     *
     * The sequence must be consumed on the thread that owns this world.
     */
    fun raycastBlockHits(ray: RayView, distanceBlocks: Double, shape: BlockRaycastShape = BlockRaycastShape.Collision): Sequence<BlockHitResult>

    /**
     * Removes the block at [position], optionally allowing the host to create its normal drops.
     */
    fun destroyBlock(position: BlockPositionView, dropItems: Boolean): Boolean

    fun playSound(position: VectorView, playback: SoundPlayback)

    fun spawnParticles(position: VectorView, effect: ParticleEffect)
}
