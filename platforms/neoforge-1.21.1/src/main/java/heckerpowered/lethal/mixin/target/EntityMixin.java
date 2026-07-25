/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.entity.EntityRemovalAccess;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.math.BoxView;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(Entity.class)
@Implements({
        @Interface(iface = EntityAccess.class, prefix = "entityAccess$"),
        @Interface(iface = EntityRemovalAccess.class, prefix = "entityRemovalAccess$")
})
abstract class EntityMixin {
    @Shadow
    public abstract int getId();

    @Shadow
    @NotNull
    public abstract UUID getUUID();

    @Shadow
    @NotNull
    public abstract Level level();

    @Shadow
    @NotNull
    public abstract Vec3 position();

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Shadow
    @NotNull
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(double x, double y, double z);

    @Shadow
    @NotNull
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setBoundingBox(@NotNull AABB boundingBox);

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract void setXRot(float pitch);

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract void setYRot(float yaw);

    @Shadow
    public abstract float getEyeHeight();

    @Shadow
    public abstract boolean hurt(@NotNull DamageSource source, float amount);

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    public abstract boolean isRemoved();

    @Shadow
    public abstract boolean onGround();

    @Shadow
    public abstract boolean isOnFire();

    @Shadow
    public abstract void discard();

    @Intrinsic
    public int entityAccess$getId() {
        return getId();
    }

    @NotNull
    public UUID entityAccess$getUuid() {
        return getUUID();
    }

    @NotNull
    public WorldAccess entityAccess$getWorld() {
        return MixinInterop.requireAccess(level(), WorldAccess.class);
    }

    @NotNull
    public VectorView entityAccess$getPosition() {
        return MixinInterop.requireAccess(position(), VectorView.class);
    }

    public void entityAccess$setPosition(@NotNull VectorView position) {
        setPos(position.getX(), position.getY(), position.getZ());
    }

    @NotNull
    public VectorView entityAccess$getVelocity() {
        return MixinInterop.requireAccess(getDeltaMovement(), VectorView.class);
    }

    public void entityAccess$setVelocity(@NotNull VectorView velocity) {
        setDeltaMovement(velocity.getX(), velocity.getY(), velocity.getZ());
    }

    @NotNull
    public BoxView entityAccess$getBoundingBox() {
        return MixinInterop.requireAccess(getBoundingBox(), BoxView.class);
    }

    public void entityAccess$setBoundingBox(@NotNull BoxView boundingBox) {
        setBoundingBox(new AABB(
                boundingBox.getMinX(),
                boundingBox.getMinY(),
                boundingBox.getMinZ(),
                boundingBox.getMaxX(),
                boundingBox.getMaxY(),
                boundingBox.getMaxZ()
        ));
    }

    public double entityAccess$getPitch() {
        return getXRot();
    }

    public void entityAccess$setPitch(double pitch) {
        setXRot((float) pitch);
    }

    public double entityAccess$getYaw() {
        return getYRot();
    }

    public void entityAccess$setYaw(double yaw) {
        setYRot((float) yaw);
    }

    public double entityAccess$getEyeHeight() {
        return getEyeHeight();
    }

    public boolean entityAccess$hurt(@NotNull DamageSourceView source, double damagePoints) {
        return hurt(MixinInterop.requireHost(source, DamageSource.class), (float) damagePoints);
    }

    @Intrinsic
    public boolean entityAccess$isAlive() {
        return isAlive();
    }

    @Intrinsic
    public boolean entityAccess$isRemoved() {
        return isRemoved();
    }

    public boolean entityAccess$isOnGround() {
        return onGround();
    }

    @Intrinsic
    public boolean entityAccess$isOnFire() {
        return isOnFire();
    }

    public void entityRemovalAccess$remove() {
        discard();
    }
}
