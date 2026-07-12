/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.sound.SoundPlayback
import heckerpowered.bridge.adapter.world.raycast.EntityRayBucketSource
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.VectorView

/**
 * Host-neutral world access.
 */
interface WorldAccess : EntityRayBucketSource {
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

    fun playSound(position: VectorView, playback: SoundPlayback)
}
