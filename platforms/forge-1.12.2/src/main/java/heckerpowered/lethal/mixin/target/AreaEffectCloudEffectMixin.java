/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import heckerpowered.lethal.mixin.impl.PotionDamageProtectionImpl;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EntityAreaEffectCloud.class)
abstract class AreaEffectCloudEffectMixin {
    @Shadow
    private UUID ownerUniqueId;
    @Unique
    private PotionDamageProtectionImpl.CloudState lethal$state;

    @Inject(method = "readEntityFromNBT", at = @At("TAIL"))
    private void lethal$readOwnerRecord(NBTTagCompound compound, CallbackInfo callback) {
        lethal$state().readOwnerRecord((EntityAreaEffectCloud) (Object) this, compound, value -> ownerUniqueId = value);
    }

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void lethal$ownerChanged(EntityLivingBase ownerIn, CallbackInfo callback) {
        lethal$state().ownerChanged((EntityAreaEffectCloud) (Object) this, ownerIn);
    }

    @WrapOperation(method = "onUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;addPotionEffect(Lnet/minecraft/potion/PotionEffect;)V"))
    private void lethal$protectDamagingEffect(EntityLivingBase target, PotionEffect potioneffectIn, Operation<Void> original) {
        lethal$state().protectDamagingEffect((EntityAreaEffectCloud) (Object) this, ownerUniqueId, target, potioneffectIn, original);
    }

    @Inject(method = "writeEntityToNBT", at = @At("TAIL"))
    private void lethal$saveTransferredOwner(NBTTagCompound compound, CallbackInfo callback) {
        lethal$state().saveTransferredOwner((EntityAreaEffectCloud) (Object) this, compound);
    }

    @Unique
    private PotionDamageProtectionImpl.CloudState lethal$state() {
        return lethal$state = PotionDamageProtectionImpl.cloudState(lethal$state);
    }
}
