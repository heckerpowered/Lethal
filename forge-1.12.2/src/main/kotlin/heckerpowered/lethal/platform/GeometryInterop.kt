package heckerpowered.lethal.platform

import heckerpowered.lethal.bridge.math.BoxView
import heckerpowered.lethal.bridge.math.Geometry
import heckerpowered.lethal.bridge.math.BlockPositionView
import heckerpowered.lethal.bridge.math.BlockPositions
import heckerpowered.lethal.bridge.math.VectorView
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object GeometryInterop {
    @JvmStatic
    fun vector(value: VectorView): Vec3d {
        return value as? Vec3d ?: Vec3d(value.x, value.y, value.z)
    }

    @JvmStatic
    fun vector(value: Vec3d): VectorView {
        return value as? VectorView ?: Geometry.vector(value.x, value.y, value.z)
    }

    @JvmStatic
    fun box(value: BoxView): AxisAlignedBB {
        return value as? AxisAlignedBB ?: AxisAlignedBB(
            value.min.x,
            value.min.y,
            value.min.z,
            value.max.x,
            value.max.y,
            value.max.z
        )
    }

    @JvmStatic
    fun box(value: AxisAlignedBB): BoxView {
        return value as? BoxView ?: Geometry.box(
            min = vector(Vec3d(value.minX, value.minY, value.minZ)),
            max = vector(Vec3d(value.maxX, value.maxY, value.maxZ))
        )
    }

    @JvmStatic
    fun blockPosition(value: BlockPositionView): BlockPos {
        return value as? BlockPos ?: BlockPos(value.x, value.y, value.z)
    }

    @JvmStatic
    fun blockPosition(value: BlockPos): BlockPositionView {
        return value as? BlockPositionView ?: BlockPositions.of(value.x, value.y, value.z)
    }
}
