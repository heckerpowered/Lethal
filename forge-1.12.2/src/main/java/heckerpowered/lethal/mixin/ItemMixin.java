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
import heckerpowered.lethal.mixin.impl.ItemAccessImpl;
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
    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    private Item self() {
        return (Item) (Object) this;
    }

    @NotNull
    public Identifier itemAccess$getIdentifier() {
        return ItemInterop.identifier(self());
    }

    @NotNull
    public ItemForm itemAccess$getForm() {
        return ItemAccessImpl.form(self());
    }

    @NotNull
    public ItemInteractionResult itemAccess$use(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull Hand hand) {
        return ItemAccessImpl.use(self(), world, user, hand);
    }

    @NotNull
    public ItemInteractionResult itemAccess$useOnBlock(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull Hand hand, @NotNull BlockPositionView position, @NotNull BlockStateAccess blockState, @NotNull BlockDirection face, @NotNull VectorView hitPosition) {
        return ItemAccessImpl.useOnBlock(self(), world, user, hand, position, face, hitPosition);
    }

    @NotNull
    public ItemInteractionResult itemAccess$interactLivingEntity(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull EntityAccess target, @NotNull Hand hand) {
        return ItemAccessImpl.interactLivingEntity(self(), stack, user, target, hand);
    }

    public void itemAccess$onUseTick(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, int remainingUseTicks) {
        ItemAccessImpl.onUseTick(self(), stack, user, remainingUseTicks);
    }

    @NotNull
    public ItemStackAccess itemAccess$finishUsing(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user) {
        return ItemAccessImpl.finishUsing(self(), stack, world, user);
    }

    public boolean itemAccess$releaseUsing(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, int remainingUseTicks) {
        return ItemAccessImpl.releaseUsing(self(), stack, world, user, remainingUseTicks);
    }

    public void itemAccess$inventoryTick(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess owner, @Nullable EquipmentSlot slot, @Nullable Integer slotIndex, boolean isSelected) {
        ItemAccessImpl.inventoryTick(self(), stack, world, owner, slotIndex, isSelected);
    }

    public void itemAccess$onCrafted(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @Nullable EntityAccess player) {
        ItemAccessImpl.onCrafted(self(), stack, world, player);
    }

    @NotNull
    public ItemUseAnimation itemAccess$getUseAnimation(@NotNull ItemStackAccess stack) {
        return ItemAccessImpl.getUseAnimation(self(), stack);
    }

    public int itemAccess$getUseDurationTicks(@NotNull ItemStackAccess stack, @Nullable EntityAccess user) {
        return ItemAccessImpl.getUseDurationTicks(self(), stack);
    }

    public double itemAccess$getDestroySpeed(@NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return ItemAccessImpl.getDestroySpeed(self(), stack, blockState);
    }

    public boolean itemAccess$canHarvest(@NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return ItemAccessImpl.canHarvest(self(), stack, blockState);
    }

    public int itemAccess$getMiningLevel(@NotNull ItemStackAccess stack, @Nullable BlockStateAccess blockState, @Nullable EntityAccess user) {
        return ItemAccessImpl.getMiningLevel(self(), stack, blockState, user);
    }

    public boolean itemAccess$mineBlock(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull BlockStateAccess blockState, @NotNull BlockPositionView position, @NotNull EntityAccess miner) {
        return ItemAccessImpl.mineBlock(self(), stack, world, blockState, position, miner);
    }

    public boolean itemAccess$hurtEnemy(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess target, @NotNull EntityAccess attacker) {
        return ItemAccessImpl.hurtEnemy(self(), stack, target, attacker);
    }

    public void itemAccess$postHurtEnemy(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess target, @NotNull EntityAccess attacker) {
        ItemAccessImpl.postHurtEnemy(self(), stack, world, target, attacker);
    }

    @Nullable
    public EquipmentSlot itemAccess$getEquipmentSlot(@NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot, @Nullable EntityAccess wearer) {
        return ItemAccessImpl.getEquipmentSlot(self(), stack, slot, wearer);
    }

    public int itemAccess$getArmorProtectionPoints(@NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot, @Nullable EntityAccess wearer) {
        return ItemAccessImpl.getArmorProtectionPoints(self(), stack, slot);
    }

    public double itemAccess$getArmorToughnessPoints(@NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot, @Nullable EntityAccess wearer) {
        return ItemAccessImpl.getArmorToughnessPoints(self(), stack, slot);
    }
}
