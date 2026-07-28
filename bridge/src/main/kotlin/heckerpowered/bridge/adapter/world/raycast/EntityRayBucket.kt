/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world.raycast

import heckerpowered.bridge.adapter.entity.EntityAccess

/**
 * A storage bucket that may contain entities hit by a ray.
 *
 * Buckets returned by [EntityRayBucketSource.getEntityRayBuckets] must be
 * ordered by [lowerBoundTime] in ascending order.
 *
 * [lowerBoundTime] is a conservative lower bound of any possible hit time
 * produced by entities in this bucket. In other words, no entity in this bucket
 * may produce a ray hit before [lowerBoundTime].
 *
 * The bucket does not need to be a literal Minecraft entity section. Old
 * versions may implement it with chunk entity lists and DDA traversal, while
 * modern versions may implement it with sparse non-empty entity sections.
 */
data class EntityRayBucket(
    val lowerBoundTime: Double,
    val entities: Iterable<EntityAccess>,
)
