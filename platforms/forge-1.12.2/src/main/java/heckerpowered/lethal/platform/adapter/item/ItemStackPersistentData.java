/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item;

import heckerpowered.bridge.resources.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemStackPersistentData {
    private ItemStackPersistentData() {
    }

    @Nullable
    public static Long getLong(@NotNull ItemStack stack, @NotNull Identifier key) {
        final NBTTagCompound tag = stack.getTagCompound();
        final String name = key.asString();
        if (tag == null || !tag.hasKey(name, Constants.NBT.TAG_LONG)) {
            return null;
        }

        return tag.getLong(name);
    }

    public static void setLong(@NotNull ItemStack stack, @NotNull Identifier key, long value) {
        getOrCreateTag(stack).setLong(key.asString(), value);
    }

    @Nullable
    public static Double getDouble(@NotNull ItemStack stack, @NotNull Identifier key) {
        final NBTTagCompound tag = stack.getTagCompound();
        final String name = key.asString();
        if (tag == null || !tag.hasKey(name, Constants.NBT.TAG_DOUBLE)) {
            return null;
        }

        return tag.getDouble(name);
    }

    public static void setDouble(@NotNull ItemStack stack, @NotNull Identifier key, double value) {
        getOrCreateTag(stack).setDouble(key.asString(), value);
    }

    public static void remove(@NotNull ItemStack stack, @NotNull Identifier key) {
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }

        tag.removeTag(key.asString());
        if (tag.isEmpty()) {
            stack.setTagCompound(null);
        }
    }

    @NotNull
    private static NBTTagCompound getOrCreateTag(@NotNull ItemStack stack) {
        final NBTTagCompound existingTag = stack.getTagCompound();
        if (existingTag != null) {
            return existingTag;
        }

        final NBTTagCompound tag = new NBTTagCompound();
        stack.setTagCompound(tag);
        return tag;
    }
}
