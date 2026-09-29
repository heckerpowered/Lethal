/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world.raycast

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.math.BoxIntersection
import heckerpowered.math.VectorView

data class EntityRayHit(
    val entity: EntityAccess,
    val intersection: BoxIntersection,
) {
    val time: Double
        get() = intersection.nearestHitTime

    val point: VectorView
        get() = intersection.nearestHitPoint
}