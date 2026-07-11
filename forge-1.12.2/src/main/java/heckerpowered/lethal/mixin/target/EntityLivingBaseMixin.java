/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess;
import heckerpowered.bridge.adapter.item.EquipmentSlot;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.lethal.platform.interop.ItemInterop;
import heckerpowered.lethal.platform.interop.ItemStackInterop;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityLivingBase.class)
@Implements(@Interface(iface = EntityEquipmentAccess.class, prefix = "entityEquipmentAccess$"))
abstract class EntityLivingBaseMixin {
    @Shadow
    @NotNull
    public abstract ItemStack getItemStackFromSlot(@NotNull EntityEquipmentSlot slotIn);

    @NotNull
    public ItemStackAccess entityEquipmentAccess$getEquippedStack(@NotNull EquipmentSlot slot) {
        return ItemStackInterop.stack(getItemStackFromSlot(ItemInterop.equipmentSlot(slot)));
    }
}
