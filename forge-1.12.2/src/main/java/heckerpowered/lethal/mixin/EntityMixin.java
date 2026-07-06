/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess;
import heckerpowered.lethal.bridge.math.BoxView;
import heckerpowered.lethal.bridge.math.Geometry;
import heckerpowered.lethal.bridge.math.VectorView;
import heckerpowered.lethal.platform.interop.GeometryInterop;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(Entity.class)
@Implements(@Interface(iface = EntityAccess.class, prefix = "entityAccess$"))
abstract class EntityMixin {
    @Shadow
    public double motionX;

    @Shadow
    public double motionY;

    @Shadow
    public double motionZ;

    @Shadow
    public float rotationPitch;

    @Shadow
    public float rotationYaw;

    @Shadow
    public boolean isDead;

    @Shadow
    public boolean onGround;

    @Shadow
    public abstract int getEntityId();

    @Shadow
    @NotNull
    public abstract UUID getUniqueID();

    @Shadow
    @NotNull
    public abstract Vec3d getPositionVector();

    @Shadow
    @NotNull
    public abstract AxisAlignedBB getEntityBoundingBox();

    @Shadow
    public abstract void setEntityBoundingBox(@NotNull AxisAlignedBB bb);

    @Shadow
    public abstract float getEyeHeight();

    @Shadow
    public abstract boolean isEntityAlive();

    @Shadow
    public abstract boolean isBurning();

    public int entityAccess$getId() {
        return getEntityId();
    }

    @NotNull
    public UUID entityAccess$getUuid() {
        return getUniqueID();
    }

    @NotNull
    public VectorView entityAccess$getPosition() {
        return GeometryInterop.vector(getPositionVector());
    }

    @NotNull
    public VectorView entityAccess$getVelocity() {
        return Geometry.vector(motionX, motionY, motionZ);
    }

    public void entityAccess$setVelocity(@NotNull VectorView value) {
        final Vec3d velocity = GeometryInterop.vector(value);
        motionX = velocity.x;
        motionY = velocity.y;
        motionZ = velocity.z;
    }

    @NotNull
    public BoxView entityAccess$getBoundingBox() {
        return GeometryInterop.box(getEntityBoundingBox());
    }

    public void entityAccess$setBoundingBox(@NotNull BoxView value) {
        setEntityBoundingBox(GeometryInterop.box(value));
    }

    public double entityAccess$getPitch() {
        return rotationPitch;
    }

    public void entityAccess$setPitch(double value) {
        rotationPitch = (float) value;
    }

    public double entityAccess$getYaw() {
        return rotationYaw;
    }

    public void entityAccess$setYaw(double value) {
        rotationYaw = (float) value;
    }

    public double entityAccess$getEyeHeight() {
        return getEyeHeight();
    }

    public boolean entityAccess$isAlive() {
        return isEntityAlive();
    }

    public boolean entityAccess$isRemoved() {
        return isDead;
    }

    public boolean entityAccess$isOnGround() {
        return onGround;
    }

    public boolean entityAccess$isOnFire() {
        return isBurning();
    }
}
