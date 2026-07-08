/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.adapter.world.raycast

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.math.BoxIntersection
import heckerpowered.lethal.bridge.math.VectorView

data class EntityRayHit(
    val entity: EntityAccess,
    val intersection: BoxIntersection,
) {
    val time: Double
        get() = intersection.nearestHitTime

    val point: VectorView
        get() = intersection.nearestHitPoint
}