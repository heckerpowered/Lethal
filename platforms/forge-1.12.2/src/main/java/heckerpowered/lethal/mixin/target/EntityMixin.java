/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.Geometry;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.lethal.platform.interop.GeometryInterop;
import heckerpowered.lethal.platform.interop.ObjectInterop;
import heckerpowered.lethal.platform.interop.WorldInterop;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
    public abstract void setPosition(double x, double y, double z);

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

    @Shadow
    public abstract boolean attackEntityFrom(@NotNull DamageSource source, float amount);

    @Shadow
    public abstract void setDead();

    @Shadow
    public World world;

    public int entityAccess$getId() {
        return getEntityId();
    }

    @NotNull
    public UUID entityAccess$getUuid() {
        return getUniqueID();
    }

    @NotNull
    public WorldAccess entityAccess$getWorld() {
        return WorldInterop.asView(world);
    }

    @NotNull
    public VectorView entityAccess$getPosition() {
        return GeometryInterop.asView(getPositionVector());
    }

    public void entityAccess$setPosition(@NotNull VectorView value) {
        setPosition(value.getX(), value.getY(), value.getZ());
    }

    @NotNull
    public VectorView entityAccess$getVelocity() {
        return Geometry.vector(motionX, motionY, motionZ);
    }

    public void entityAccess$setVelocity(@NotNull VectorView value) {
        final Vec3d velocity = GeometryInterop.asHost(value);
        motionX = velocity.x;
        motionY = velocity.y;
        motionZ = velocity.z;
    }

    @NotNull
    public BoxView entityAccess$getBoundingBox() {
        return GeometryInterop.asView(getEntityBoundingBox());
    }

    public void entityAccess$setBoundingBox(@NotNull BoxView value) {
        setEntityBoundingBox(GeometryInterop.asHost(value));
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

    public boolean entityAccess$hurt(@NotNull DamageSourceView source, double damagePoints) {
        return attackEntityFrom(ObjectInterop.asHost(source), (float) damagePoints);
    }

    public void entityAccess$remove() {
        setDead();
    }
}
