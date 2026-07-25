/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.item;

import heckerpowered.bridge.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemStackPersistentData {
    private ItemStackPersistentData() {
    }

    @Nullable
    public static Long getLong(@NotNull ItemStack stack, @NotNull Identifier key) {
        final CompoundTag tag = lethal$data(stack);
        final String name = key.asString();
        if (tag == null || !tag.contains(name, Tag.TAG_LONG)) {
            return null;
        }

        return tag.getLong(name);
    }

    public static void setLong(@NotNull ItemStack stack, @NotNull Identifier key, long value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(key.asString(), value));
    }

    @Nullable
    public static Double getDouble(@NotNull ItemStack stack, @NotNull Identifier key) {
        final CompoundTag tag = lethal$data(stack);
        final String name = key.asString();
        if (tag == null || !tag.contains(name, Tag.TAG_DOUBLE)) {
            return null;
        }

        return tag.getDouble(name);
    }

    public static void setDouble(@NotNull ItemStack stack, @NotNull Identifier key, double value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(key.asString(), value));
    }

    public static void remove(@NotNull ItemStack stack, @NotNull Identifier key) {
        final CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(key.asString())) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(key.asString()));
    }

    @Nullable
    private static CompoundTag lethal$data(@NotNull ItemStack stack) {
        final CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? null : customData.copyTag();
    }
}
