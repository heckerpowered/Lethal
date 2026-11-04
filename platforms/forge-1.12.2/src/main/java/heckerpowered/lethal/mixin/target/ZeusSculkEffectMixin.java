/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.lethal.mixin.impl.ZeusSculkEffectImpl;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
abstract class ZeusSculkEffectMixin {
    @Inject(method = "isPotionApplicable", at = @At("HEAD"), cancellable = true)
    private void lethal$rejectVisualImpairment(PotionEffect potioneffectIn, CallbackInfoReturnable<Boolean> callback) {
        ZeusSculkEffectImpl.rejectVisualImpairment((EntityLivingBase) (Object) this, potioneffectIn, callback);
    }

    @Inject(method = "updatePotionEffects", at = @At("HEAD"))
    private void lethal$clearVisualImpairment(CallbackInfo callback) {
        ZeusSculkEffectImpl.clearVisualImpairment((EntityLivingBase) (Object) this);
    }
}

