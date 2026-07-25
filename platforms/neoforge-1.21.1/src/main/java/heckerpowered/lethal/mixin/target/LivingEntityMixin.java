/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess;
import heckerpowered.bridge.adapter.entity.EntityExecutionAccess;
import heckerpowered.bridge.adapter.entity.GlowingAccess;
import heckerpowered.bridge.adapter.entity.LivingEntityAccess;
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
@Implements({
        @Interface(iface = EntityEquipmentAccess.class, prefix = "entityEquipmentAccess$"),
        @Interface(iface = LivingEntityAccess.class, prefix = "livingEntityAccess$"),
        @Interface(iface = EntityExecutionAccess.class, prefix = "entityExecutionAccess$"),
        @Interface(iface = GlowingAccess.class, prefix = "glowingAccess$")
})
abstract class LivingEntityMixin {
    @Unique
    private boolean lethal$executed;

    @Shadow
    public abstract float getHealth();

    @Shadow
    public abstract void setHealth(float health);

    @Shadow
    public abstract float getMaxHealth();

    @Shadow
    public abstract void die(@NotNull DamageSource source);

    @Shadow
    public abstract boolean addEffect(@NotNull MobEffectInstance effect);

    @Shadow
    @NotNull
    public abstract ItemStack getItemBySlot(@NotNull net.minecraft.world.entity.EquipmentSlot slot);

    public double livingEntityAccess$getHealth() {
        return getHealth();
    }

    public void livingEntityAccess$setHealth(double health) {
        setHealth((float) health);
    }

    public double livingEntityAccess$getMaximumHealth() {
        return getMaxHealth();
    }

    @NotNull
    public ItemStackAccess entityEquipmentAccess$getEquippedStack(@NotNull heckerpowered.bridge.adapter.item.EquipmentSlot slot) {
        final ItemStack stack = getItemBySlot(lethal$equipmentSlot(slot));
        return MixinInterop.requireAccess(stack, ItemStackAccess.class);
    }

    public void entityExecutionAccess$execute(@NotNull DamageSourceView source) {
        lethal$executed = true;
        setHealth(0.0F);
        die(MixinInterop.requireHost(source, DamageSource.class));
    }

    public void glowingAccess$glowFor(int durationTicks) {
        addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks));
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void lethal$keepExecutedHealthAtZero(CallbackInfoReturnable<Float> callback) {
        if (lethal$executed) {
            callback.setReturnValue(0.0F);
        }
    }

    @Unique
    private static net.minecraft.world.entity.EquipmentSlot lethal$equipmentSlot(heckerpowered.bridge.adapter.item.EquipmentSlot slot) {
        return switch (slot) {
            case MainHand -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OffHand -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case Feet -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case Legs -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case Chest -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case Head -> net.minecraft.world.entity.EquipmentSlot.HEAD;
        };
    }
}
