/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.MixinInterop;
import heckerpowered.bridge.adapter.item.ItemAccess;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.platform.adapter.item.ItemStackPersistentData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

@Mixin(ItemStack.class)
@Implements({
        @Interface(iface = ItemStackAccess.class, prefix = "itemStackAccess$"),
        @Interface(iface = PersistentDataAccess.class, prefix = "persistentDataAccess$")
})
abstract class ItemStackMixin {
    @Shadow
    @NotNull
    public abstract Item getItem();

    @Shadow
    public abstract int getCount();

    @Shadow
    public abstract void setCount(int count);

    @Shadow
    public abstract int getDamageValue();

    @Shadow
    public abstract void setDamageValue(int damage);

    @Shadow
    public abstract int getMaxStackSize();

    @Shadow
    public abstract int getMaxDamage();

    @Shadow
    public abstract boolean isEmpty();

    @Shadow
    public abstract boolean isDamageableItem();

    @Shadow
    public abstract boolean isDamaged();

    @NotNull
    public ItemAccess itemStackAccess$getItem() {
        return MixinInterop.requireAccess(getItem(), ItemAccess.class);
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
        return getDamageValue();
    }

    public void itemStackAccess$setDamagePoints(int damagePoints) {
        setDamageValue(damagePoints);
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
        return isDamageableItem();
    }

    @Intrinsic
    public boolean itemStackAccess$isDamaged() {
        return isDamaged();
    }

    @Nullable
    public Long persistentDataAccess$getLong(@NotNull Identifier key) {
        return ItemStackPersistentData.getLong(lethal$self(), key);
    }

    public void persistentDataAccess$setLong(@NotNull Identifier key, long value) {
        ItemStackPersistentData.setLong(lethal$self(), key, value);
    }

    @Nullable
    public Double persistentDataAccess$getDouble(@NotNull Identifier key) {
        return ItemStackPersistentData.getDouble(lethal$self(), key);
    }

    public void persistentDataAccess$setDouble(@NotNull Identifier key, double value) {
        ItemStackPersistentData.setDouble(lethal$self(), key, value);
    }

    public void persistentDataAccess$remove(@NotNull Identifier key) {
        ItemStackPersistentData.remove(lethal$self(), key);
    }

    @Unique
    private ItemStack lethal$self() {
        return (ItemStack) (Object) this;
    }
}
