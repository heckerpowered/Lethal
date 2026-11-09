/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import heckerpowered.lethal.mixin.impl.PotionDamageProtectionImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
abstract class AreaEffectCloudEffectMixin {
    @Shadow
    public EntityReference<LivingEntity> owner;

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean lethal$protectDamagingEffect(LivingEntity target, MobEffectInstance newEffect, Entity source, Operation<Boolean> original) {
        return PotionDamageProtectionImpl.protectCloudEffect((AreaEffectCloud) (Object) this, owner, target, newEffect, source, original);
    }
}
