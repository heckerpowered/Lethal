/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.VectorView
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

fun BlockPositionView.blockPosition() = if (this is BlockPos) this else BlockPos(x, y, z)

fun BlockPos.blockPosition() = requireAccess<BlockPositionView>(this)

fun BoxView.box() = if (this is AABB) this else AABB(minX, minY, minZ, maxX, maxY, maxZ)

fun AABB.box() = requireAccess<BoxView>(this)

fun VectorView.vector() = if (this is Vec3) this else Vec3(x, y, z)

fun Vec3.vector() = requireAccess<VectorView>(this)
