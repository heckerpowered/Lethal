/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.math.BlockDirection
import heckerpowered.bridge.math.BlockPositionView
import heckerpowered.bridge.math.BoxView
import heckerpowered.bridge.math.VectorView
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

fun BlockPositionView.asHost() = if (this is BlockPos) this else BlockPos(x, y, z)

fun BlockPos.asView() = requireAccess<BlockPositionView>(this)

fun BoxView.asHost() = if (this is AABB) this else AABB(minX, minY, minZ, maxX, maxY, maxZ)

fun AABB.asView() = requireAccess<BoxView>(this)

fun VectorView.asHost() = if (this is Vec3) this else Vec3(x, y, z)

fun Vec3.asView() = requireAccess<VectorView>(this)

fun BlockDirection.asHost(): Direction {
    return when (this) {
        BlockDirection.Down -> Direction.DOWN
        BlockDirection.Up -> Direction.UP
        BlockDirection.North -> Direction.NORTH
        BlockDirection.South -> Direction.SOUTH
        BlockDirection.West -> Direction.WEST
        BlockDirection.East -> Direction.EAST
    }
}

fun Direction.asView(): BlockDirection {
    return when (this) {
        Direction.DOWN -> BlockDirection.Down
        Direction.UP -> BlockDirection.Up
        Direction.NORTH -> BlockDirection.North
        Direction.SOUTH -> BlockDirection.South
        Direction.WEST -> BlockDirection.West
        Direction.EAST -> BlockDirection.East
    }
}
