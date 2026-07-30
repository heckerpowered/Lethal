/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.effect.StatusEffectAccess;
import heckerpowered.bridge.adapter.effect.StatusEffectInstance;
import heckerpowered.bridge.adapter.entity.*;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.bridge.adapter.item.EquipmentSlot;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.lethal.platform.interop.ItemInterop;
import heckerpowered.lethal.platform.interop.ItemStackInterop;
import heckerpowered.lethal.platform.interop.ObjectInterop;
import heckerpowered.lethal.platform.interop.StatusEffectInterop;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
@Implements({
        @Interface(iface = DeferredExperienceDropAccess.class, prefix = "deferredExperienceDropAccess$"),
        @Interface(iface = EntityEquipmentAccess.class, prefix = "entityEquipmentAccess$"),
        @Interface(iface = LivingEntityAccess.class, prefix = "livingEntityAccess$"),
        @Interface(iface = EntityExecutionAccess.class, prefix = "entityExecutionAccess$"),
        @Interface(iface = StatusEffectAccess.class, prefix = "statusEffectAccess$")
})
abstract class EntityLivingBaseMixin {
    @Shadow
    public int deathTime;
    @Unique
    private boolean lethal$executed;
    @Unique
    @Nullable
    private PlayerAccess lethal$deferredExperienceReceiver;

    @Shadow
    public abstract float getHealth();

    @Shadow
    public abstract void setHealth(float health);

    @Shadow
    public abstract float getMaxHealth();

    @Shadow
    public abstract boolean attackEntityFrom(@NotNull DamageSource source, float amount);

    @Shadow
    public abstract void onDeath(@NotNull DamageSource cause);

    @Shadow
    @NotNull
    public abstract ItemStack getItemStackFromSlot(@NotNull EntityEquipmentSlot slotIn);

    public double livingEntityAccess$getHealth() {
        return getHealth();
    }

    public void livingEntityAccess$setHealth(double value) {
        setHealth((float) value);
    }

    public double livingEntityAccess$getMaximumHealth() {
        return getMaxHealth();
    }

    public void statusEffectAccess$addStatusEffect(@NotNull StatusEffectInstance effect) {
        lethal$self().addPotionEffect(StatusEffectInterop.asHost(effect));
    }

    public void entityExecutionAccess$execute(@NotNull DamageSourceView source) {
        lethal$executed = true;
        setHealth(0.0F);

        final DamageSource nativeSource = ObjectInterop.asHost(source);
        attackEntityFrom(nativeSource, Float.POSITIVE_INFINITY);
        onDeath(nativeSource);
    }

    public void deferredExperienceDropAccess$sendDeferredExperienceTo(@NotNull PlayerAccess receiver) {
        lethal$deferredExperienceReceiver = receiver;
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void lethal$keepExecutedHealthAtZero(CallbackInfoReturnable<Float> callback) {
        if (lethal$executed) {
            callback.setReturnValue(0.0F);
        }
    }

    // Minecraft 1.12.2 creates experience orbs 20 ticks after onDeath returns, too late for the
    // common hit effect's immediate entity scan. Redirect only those delayed orbs once capture
    // has been requested for this death.
    @Redirect(
            method = "onDeathUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean lethal$captureDeferredExperience(World world, Entity entityIn) {
        final PlayerAccess receiver = lethal$deferredExperienceReceiver;
        if (receiver != null && entityIn instanceof EntityXPOrb) {
            receiver.addExperiencePoints(((EntityXPOrb) entityIn).getXpValue());
            return true;
        }

        return world.spawnEntity(entityIn);
    }

    @Unique
    private EntityLivingBase lethal$self() {
        return (EntityLivingBase) (Object) this;
    }

    @Inject(method = "onDeathUpdate", at = @At("TAIL"))
    private void lethal$releaseDeferredExperienceReceiver(CallbackInfo callback) {
        if (deathTime >= 20) {
            lethal$deferredExperienceReceiver = null;
        }
    }

    @NotNull
    public ItemStackAccess entityEquipmentAccess$getEquippedStack(@NotNull EquipmentSlot slot) {
        return ItemStackInterop.asView(getItemStackFromSlot(ItemInterop.asHost(slot)));
    }
}
