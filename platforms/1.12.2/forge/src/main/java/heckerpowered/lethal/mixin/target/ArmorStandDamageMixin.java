/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.DamagePipelineImpl;

import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityArmorStand.class)
abstract class ArmorStandDamageMixin {
    @Inject(method = "attackEntityFrom", at = @At("HEAD"), cancellable = true)
    private void lethal$protectDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        DamagePipelineImpl.protectArmorStandDamage((EntityArmorStand) (Object) this, source, callback);
    }
}
