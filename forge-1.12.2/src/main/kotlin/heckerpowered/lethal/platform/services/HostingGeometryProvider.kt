/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.math.BoxView
import heckerpowered.lethal.bridge.math.GeometryProvider
import heckerpowered.lethal.bridge.math.VectorView
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d

class HostingGeometryProvider : GeometryProvider {
    override fun vector(x: Double, y: Double, z: Double): VectorView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return Vec3d(x, y, z) as VectorView
    }

    override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ) as BoxView
    }
}
