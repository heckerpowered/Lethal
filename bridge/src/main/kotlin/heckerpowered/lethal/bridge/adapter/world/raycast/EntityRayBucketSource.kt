/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.world.raycast

import heckerpowered.lethal.bridge.math.RayView

/**
 * Provides ordered entity buckets for ray queries.
 *
 * Implementations are version-specific:
 *
 * - 1.12.2 may use DDA traversal over chunk entity sections.
 * - Modern versions may use sparse non-empty entity section storage.
 *
 * The returned buckets must be ordered by [EntityRayBucket.lowerBoundTime].
 * Implementations should avoid returning the same logical entity more than once,
 * or duplicate hits may be produced by the upper raycast algorithm.
 */
interface EntityRayBucketSource {
    fun getEntityRayBuckets(ray: RayView, length: Double): Sequence<EntityRayBucket>
}