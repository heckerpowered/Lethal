/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess;
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageFeature;
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.lethal.bridge.math.VectorView;
import heckerpowered.lethal.bridge.resources.Identifier;
import heckerpowered.lethal.platform.GeometryInterop;
import heckerpowered.lethal.platform.ObjectInterop;
import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(DamageSource.class)
@Implements(@Interface(iface = DamageSourceView.class, prefix = "damageSourceView$"))
abstract
class DamageSourceMixin {
    @Shadow
    @Nullable
    public abstract Entity getImmediateSource();

    @Shadow
    @Nullable
    public abstract Entity getTrueSource();

    @Shadow
    @Nullable
    public abstract Vec3d getDamageLocation();

    @Nullable
    public EntityAccess damageSourceView$getDirectEntity() {
        return ObjectInterop.entityOrNull(getImmediateSource());
    }

    @Nullable
    public EntityAccess damageSourceView$getCausingEntity() {
        return ObjectInterop.entityOrNull(getTrueSource());
    }

    @Nullable
    public VectorView damageSourceView$getPosition() {
        final Vec3d position = getDamageLocation();
        if (position == null) return null;

        return GeometryInterop.vector(position);
    }

    @NotNull
    public Identifier damageSourceView$getType() {
        return ObjectInterop.damageSourceType((DamageSource) (Object) this);
    }

    public boolean damageSourceView$has(@NotNull DamageFeature feature) {
        return ObjectInterop.hasDamageFeature((DamageSource) (Object) this, feature);
    }
}
