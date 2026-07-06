/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin;

import heckerpowered.lethal.bridge.adapter.block.BlockStateAccess;
import heckerpowered.lethal.bridge.adapter.entity.EntityAccess;
import heckerpowered.lethal.bridge.adapter.item.*;
import heckerpowered.lethal.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.lethal.bridge.adapter.world.WorldAccess;
import heckerpowered.lethal.bridge.math.BlockDirection;
import heckerpowered.lethal.bridge.math.BlockPositionView;
import heckerpowered.lethal.bridge.math.VectorView;
import heckerpowered.lethal.bridge.resources.Identifier;
import heckerpowered.lethal.platform.interop.ItemInterop;
import net.minecraft.item.Item;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

@Mixin(Item.class)
@Implements(@Interface(iface = ItemAccess.class, prefix = "itemAccess$"))
abstract class ItemMixin {
    @Unique
    private final Item self(){
        return (Item) (Object) this;
    }

    @NotNull
    public Identifier itemAccess$getIdentifier() {
        return ItemInterop.identifier(ItemInterop.item(this));
    }

    @NotNull
    public ItemForm itemAccess$getForm() {
        return ItemInterop.form(ItemInterop.item(this));
    }

    @NotNull
    public ItemInteractionResult itemAccess$use(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull Hand hand) {
        return ItemInterop.use(ItemInterop.item(this), world, user, hand);
    }

    @NotNull
    public ItemInteractionResult itemAccess$useOnBlock(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull Hand hand, @NotNull BlockPositionView position, @NotNull BlockStateAccess blockState, @NotNull BlockDirection face, @NotNull VectorView hitPosition) {
        return ItemInterop.useOnBlock(ItemInterop.item(this), world, user, hand, position, face, hitPosition);
    }

    @NotNull
    public ItemInteractionResult itemAccess$interactLivingEntity(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull EntityAccess target, @NotNull Hand hand) {
        return ItemInterop.interactLivingEntity(ItemInterop.item(this), stack, user, target, hand);
    }

    public void itemAccess$onUseTick(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, int remainingUseTicks) {
        ItemInterop.onUseTick(ItemInterop.item(this), stack, user, remainingUseTicks);
    }

    @NotNull
    public ItemStackAccess itemAccess$finishUsing(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user) {
        return ItemInterop.finishUsing(ItemInterop.item(this), stack, world, user);
    }

    public boolean itemAccess$releaseUsing(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, int remainingUseTicks) {
        return ItemInterop.releaseUsing(ItemInterop.item(this), stack, world, user, remainingUseTicks);
    }

    public void itemAccess$inventoryTick(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess owner, @Nullable EquipmentSlot slot, @Nullable Integer slotIndex, boolean isSelected) {
        ItemInterop.inventoryTick(ItemInterop.item(this), stack, world, owner, slotIndex, isSelected);
    }

    public void itemAccess$onCrafted(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @Nullable EntityAccess player) {
        ItemInterop.onCrafted(ItemInterop.item(this), stack, world, player);
    }

    @NotNull
    public ItemUseAnimation itemAccess$getUseAnimation(@NotNull ItemStackAccess stack) {
        return ItemInterop.getUseAnimation(ItemInterop.item(this), stack);
    }

    public int itemAccess$getUseDurationTicks(@NotNull ItemStackAccess stack, @Nullable EntityAccess user) {
        return ItemInterop.getUseDurationTicks(ItemInterop.item(this), stack);
    }

    public double itemAccess$getDestroySpeed(@NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return ItemInterop.getDestroySpeed(ItemInterop.item(this), stack, blockState);
    }

    public boolean itemAccess$canHarvest(@NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return ItemInterop.canHarvest(ItemInterop.item(this), stack, blockState);
    }

    public int itemAccess$getMiningLevel(@NotNull ItemStackAccess stack, @Nullable BlockStateAccess blockState, @Nullable EntityAccess user) {
        return ItemInterop.getMiningLevel(ItemInterop.item(this), stack, blockState, user);
    }

    public boolean itemAccess$mineBlock(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull BlockStateAccess blockState, @NotNull BlockPositionView position, @NotNull EntityAccess miner) {
        return ItemInterop.mineBlock(ItemInterop.item(this), stack, world, blockState, position, miner);
    }

    public boolean itemAccess$hurtEnemy(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess target, @NotNull EntityAccess attacker) {
        return ItemInterop.hurtEnemy(ItemInterop.item(this), stack, target, attacker);
    }

    public void itemAccess$postHurtEnemy(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess target, @NotNull EntityAccess attacker) {
        ItemInterop.postHurtEnemy(ItemInterop.item(this), stack, world, target, attacker);
    }

    @Nullable
    public EquipmentSlot itemAccess$getEquipmentSlot(@NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot, @Nullable EntityAccess wearer) {
        return ItemInterop.getEquipmentSlot(ItemInterop.item(this), stack, slot, wearer);
    }

    public int itemAccess$getArmorProtectionPoints(@NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot, @Nullable EntityAccess wearer) {
        return ItemInterop.getArmorProtectionPoints(ItemInterop.item(this), stack, slot);
    }

    public double itemAccess$getArmorToughnessPoints(@NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot, @Nullable EntityAccess wearer) {
        return ItemInterop.getArmorToughnessPoints(ItemInterop.item(this), stack, slot);
    }
}
