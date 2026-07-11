/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.item.ItemAccess;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.lethal.platform.interop.ItemInterop;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;

@Mixin(ItemStack.class)
@Implements(@Interface(iface = ItemStackAccess.class, prefix = "itemStackAccess$"))
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
        return ItemInterop.item(getItem());
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
}
