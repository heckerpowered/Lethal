/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.GeometryProvider
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.platform.interop.box
import heckerpowered.lethal.platform.interop.vector
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class HostingGeometryProvider : GeometryProvider {
    override fun vector(x: Double, y: Double, z: Double): VectorView {
        return Vec3(x, y, z).vector()
    }

    override fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): BoxView {
        return AABB(minX, minY, minZ, maxX, maxY, maxZ).box()
    }
}
