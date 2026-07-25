/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services;

import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.GeometryProvider;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public final class HostingGeometryProvider implements GeometryProvider {
    @Override
    @NotNull
    public VectorView vector(double x, double y, double z) {
        return MixinInterop.requireAccess(new Vec3(x, y, z), VectorView.class);
    }

    @Override
    @NotNull
    public BoxView box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return MixinInterop.requireAccess(new AABB(minX, minY, minZ, maxX, maxY, maxZ), BoxView.class);
    }
}
