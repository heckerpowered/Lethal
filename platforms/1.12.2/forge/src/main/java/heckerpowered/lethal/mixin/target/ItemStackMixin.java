/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.item.ItemAccess;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess;
import heckerpowered.bridge.adapter.item.stack.PersistentValueInput;
import heckerpowered.bridge.adapter.item.stack.PersistentValueOutput;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.mixin.impl.ItemStackAccessImpl;
import heckerpowered.lethal.platform.adapter.item.ItemStackPersistentData;
import heckerpowered.lethal.platform.interop.ItemInterop;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(ItemStack.class)
@Implements({
        @Interface(iface = ItemStackAccess.class, prefix = "itemStackAccess$"),
        @Interface(iface = PersistentDataAccess.class, prefix = "persistentDataAccess$")
})
abstract class ItemStackMixin {
    @Shadow
    public abstract Item getItem();

    @Shadow
    public abstract int getCount();

    @Shadow
    public abstract void setCount(int size);

    @Shadow
    public abstract int getItemDamage();

    @Shadow
    public abstract void setItemDamage(int meta);

    @Shadow
    public abstract int getMaxStackSize();

    @Shadow
    public abstract int getMaxDamage();

    @Shadow
    public abstract boolean isEmpty();

    @Shadow
    public abstract boolean isItemStackDamageable();

    @Shadow
    public abstract boolean isItemDamaged();

    @NotNull
    public ItemAccess itemStackAccess$getItem() {
        return ItemInterop.asView(getItem());
    }

    @Intrinsic
    public int itemStackAccess$getCount() {
        return getCount();
    }

    @Intrinsic
    public void itemStackAccess$setCount(int count) {
        setCount(count);
    }

    public int itemStackAccess$getDamagePoints() {
        return getItemDamage();
    }

    public void itemStackAccess$setDamagePoints(int damagePoints) {
        setItemDamage(damagePoints);
    }

    public int itemStackAccess$getMaxStackCount() {
        return getMaxStackSize();
    }

    public int itemStackAccess$getMaxDamagePoints() {
        return getMaxDamage();
    }

    @Intrinsic
    public boolean itemStackAccess$isEmpty() {
        return isEmpty();
    }

    public boolean itemStackAccess$isDamageable() {
        return isItemStackDamageable();
    }

    public boolean itemStackAccess$isDamaged() {
        return isItemDamaged();
    }

    public int itemStackAccess$getEnchantmentLevel(@NotNull Identifier enchantment) {
        return ItemStackAccessImpl.getEnchantmentLevel((ItemStack) (Object) this, enchantment);
    }

    public boolean itemStackAccess$supportsEnchantment(@NotNull Identifier enchantment, @NotNull WorldAccess world) {
        return ItemStackAccessImpl.supportsEnchantment((ItemStack) (Object) this, enchantment, world);
    }

    public void itemStackAccess$addEnchantment(@NotNull Identifier enchantment, int level, @NotNull WorldAccess world) {
        ItemStackAccessImpl.addEnchantment((ItemStack) (Object) this, enchantment, level, world);
    }

    @NotNull
    public PersistentValueInput persistentDataAccess$getInput() {
        return ItemStackPersistentData.input((ItemStack) (Object) this);
    }

    @NotNull
    public PersistentValueOutput persistentDataAccess$getOutput() {
        return ItemStackPersistentData.output((ItemStack) (Object) this);
    }
}
