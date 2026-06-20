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

    override fun box(min: VectorView, max: VectorView): BoxView {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return AxisAlignedBB(min as Vec3d, max as Vec3d) as BoxView
    }
}