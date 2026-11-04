/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.ZeusSculkEffectImpl;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class ZeusSculkEffectMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void lethal$rejectVisualImpairment(MobEffectInstance effect, CallbackInfoReturnable<Boolean> callback) {
        ZeusSculkEffectImpl.rejectVisualImpairment((LivingEntity) (Object) this, effect, callback);
    }

    @Inject(method = "tickEffects", at = @At("HEAD"))
    private void lethal$clearVisualImpairment(CallbackInfo callback) {
        ZeusSculkEffectImpl.clearVisualImpairment((LivingEntity) (Object) this);
    }
}

