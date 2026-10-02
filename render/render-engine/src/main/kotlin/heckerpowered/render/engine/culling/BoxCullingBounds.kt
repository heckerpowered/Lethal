/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.culling

import heckerpowered.math.*
import heckerpowered.render.engine.view.Frustum

/**
 * Uses a conservative local box to exclude contributions completely outside the view.
 *
 * Construction copies the coordinates, without deriving bounds from geometry. Rotation, scale,
 * reflection and shear are applied in Double coordinates. Invalid bounds, non-finite placement,
 * or overflow leave the contribution eligible rather than proving exclusion.
 */
class BoxCullingBounds(bounds: BoxView) : CullingBounds {
    private val localBox = Geometry.box(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)

    override fun canCull(frustum: Frustum, localToWorld: AffineTransformView): Boolean {
        if (!localBox.isUsableForCulling()) return false

        val placement = AffineTransforms.copyOf(localToWorld)
        if (!placement.isFinite()) return false

        val worldBox = localBox.transformedBy(placement)
        return worldBox.isUsableForCulling() && !frustum.intersects(worldBox)
    }
}

private fun BoxView.isUsableForCulling(): Boolean =
    minX.isFinite() && minY.isFinite() && minZ.isFinite() &&
            maxX.isFinite() && maxY.isFinite() && maxZ.isFinite() &&
            minX <= maxX && minY <= maxY && minZ <= maxZ