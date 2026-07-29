/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.math.*
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object GeometryInterop {
    @JvmStatic
    fun asHost(value: VectorView): Vec3d {
        return value as? Vec3d ?: Vec3d(value.x, value.y, value.z)
    }

    @JvmStatic
    fun asView(value: Vec3d): VectorView {
        return value as? VectorView ?: Geometry.vector(value.x, value.y, value.z)
    }

    @JvmStatic
    fun asHost(value: BoxView): AxisAlignedBB {
        return value as? AxisAlignedBB ?: AxisAlignedBB(value.minX, value.minY, value.minZ, value.maxX, value.maxY, value.maxZ)
    }

    @JvmStatic
    fun asView(value: AxisAlignedBB): BoxView {
        return value as? BoxView ?: Geometry.box(value.minX, value.minY, value.minZ, value.maxX, value.maxY, value.maxZ)
    }

    fun asHost(value: BlockPositionView): BlockPos {
        return value as? BlockPos ?: BlockPos(value.x, value.y, value.z)
    }

    fun asView(value: BlockPos): BlockPositionView {
        return value as? BlockPositionView ?: BlockPositions.of(value.x, value.y, value.z)
    }
}

fun VectorView.asHost(): Vec3d {
    return GeometryInterop.asHost(this)
}

fun Vec3d.asView(): VectorView {
    return GeometryInterop.asView(this)
}

fun BoxView.asHost(): AxisAlignedBB {
    return GeometryInterop.asHost(this)
}

fun AxisAlignedBB.asView(): BoxView {
    return GeometryInterop.asView(this)
}

fun BlockPositionView.asHost(): BlockPos {
    return GeometryInterop.asHost(this)
}

fun BlockPos.asView(): BlockPositionView {
    return GeometryInterop.asView(this)
}

fun EnumFacing.asView(): BlockDirection {
    return when (this) {
        EnumFacing.DOWN -> BlockDirection.Down
        EnumFacing.UP -> BlockDirection.Up
        EnumFacing.NORTH -> BlockDirection.North
        EnumFacing.SOUTH -> BlockDirection.South
        EnumFacing.WEST -> BlockDirection.West
        EnumFacing.EAST -> BlockDirection.East
    }
}

fun BlockDirection.asHost(): EnumFacing {
    return when (this) {
        BlockDirection.Down -> EnumFacing.DOWN
        BlockDirection.Up -> EnumFacing.UP
        BlockDirection.North -> EnumFacing.NORTH
        BlockDirection.South -> EnumFacing.SOUTH
        BlockDirection.West -> EnumFacing.WEST
        BlockDirection.East -> EnumFacing.EAST
    }
}
