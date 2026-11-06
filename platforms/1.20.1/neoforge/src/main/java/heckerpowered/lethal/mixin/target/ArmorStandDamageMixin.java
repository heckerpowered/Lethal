/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.DamagePipelineImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStand.class)
abstract class ArmorStandDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void lethal$protectDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        DamagePipelineImpl.protectArmorStandDamage((ArmorStand) (Object) this, source, callback);
    }
}
