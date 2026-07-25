/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.target;

import heckerpowered.bridge.adapter.block.BlockStateAccess;
import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.item.EquipmentSlot;
import heckerpowered.bridge.adapter.item.Hand;
import heckerpowered.bridge.adapter.item.ItemAccess;
import heckerpowered.bridge.adapter.item.ItemForm;
import heckerpowered.bridge.adapter.item.ItemInteractionResult;
import heckerpowered.bridge.adapter.item.ItemUseAnimation;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.math.BlockDirection;
import heckerpowered.bridge.math.BlockPositionView;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.mixin.impl.ItemAccessImpl;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Item.class)
@Implements(@Interface(iface = ItemAccess.class, prefix = "itemAccess$"))
abstract class ItemMixin {
    @NotNull
    public Identifier itemAccess$getIdentifier() {
        return ItemAccessImpl.identifier(lethal$self());
    }

    @NotNull
    public ItemForm itemAccess$getForm() {
        return ItemAccessImpl.form(lethal$self());
    }

    @NotNull
    public ItemInteractionResult itemAccess$use(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull Hand hand) {
        return ItemAccessImpl.use(lethal$self(), world, user, hand);
    }

    @NotNull
    public ItemInteractionResult itemAccess$useOnBlock(
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess user,
            @NotNull Hand hand,
            @NotNull BlockPositionView position,
            @NotNull BlockStateAccess blockState,
            @NotNull BlockDirection face,
            @NotNull VectorView hitPosition
    ) {
        return ItemAccessImpl.useOnBlock(lethal$self(), stack, world, user, hand, position, face, hitPosition);
    }

    @NotNull
    public ItemInteractionResult itemAccess$interactLivingEntity(
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess user,
            @NotNull EntityAccess target,
            @NotNull Hand hand
    ) {
        return ItemAccessImpl.interactLivingEntity(lethal$self(), stack, user, target, hand);
    }

    public void itemAccess$onUseTick(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, int remainingUseTicks) {
        ItemAccessImpl.onUseTick(lethal$self(), stack, world, user, remainingUseTicks);
    }

    @NotNull
    public ItemStackAccess itemAccess$finishUsing(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user) {
        return ItemAccessImpl.finishUsing(lethal$self(), stack, world, user);
    }

    public boolean itemAccess$releaseUsing(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @NotNull EntityAccess user, int remainingUseTicks) {
        return ItemAccessImpl.releaseUsing(lethal$self(), stack, world, user, remainingUseTicks);
    }

    public void itemAccess$inventoryTick(
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess owner,
            @Nullable EquipmentSlot slot,
            @Nullable Integer slotIndex,
            boolean isSelected
    ) {
        ItemAccessImpl.inventoryTick(lethal$self(), stack, world, owner, slotIndex, isSelected);
    }

    public void itemAccess$onCrafted(@NotNull ItemStackAccess stack, @NotNull WorldAccess world, @Nullable EntityAccess player) {
        ItemAccessImpl.onCrafted(lethal$self(), stack, world, player);
    }

    @NotNull
    public ItemUseAnimation itemAccess$getUseAnimation(@NotNull ItemStackAccess stack) {
        return ItemAccessImpl.getUseAnimation(lethal$self(), stack);
    }

    public int itemAccess$getUseDurationTicks(@NotNull ItemStackAccess stack, @Nullable EntityAccess user) {
        return ItemAccessImpl.getUseDurationTicks(lethal$self(), stack, user);
    }

    public double itemAccess$getDestroySpeed(@NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return ItemAccessImpl.getDestroySpeed(lethal$self(), stack, blockState);
    }

    public boolean itemAccess$canHarvest(@NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return ItemAccessImpl.canHarvest(lethal$self(), stack, blockState);
    }

    public int itemAccess$getMiningLevel(@NotNull ItemStackAccess stack, @Nullable BlockStateAccess blockState, @Nullable EntityAccess user) {
        return ItemAccessImpl.getMiningLevel(lethal$self());
    }

    public boolean itemAccess$mineBlock(
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull BlockStateAccess blockState,
            @NotNull BlockPositionView position,
            @NotNull EntityAccess miner
    ) {
        return ItemAccessImpl.mineBlock(lethal$self(), stack, world, blockState, position, miner);
    }

    public boolean itemAccess$hurtEnemy(
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess target,
            @NotNull EntityAccess attacker
    ) {
        return ItemAccessImpl.hurtEnemy(lethal$self(), stack, target, attacker);
    }

    public void itemAccess$postHurtEnemy(
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess target,
            @NotNull EntityAccess attacker
    ) {
        ItemAccessImpl.postHurtEnemy(lethal$self(), stack, target, attacker);
    }

    @Nullable
    public EquipmentSlot itemAccess$getEquipmentSlot(
            @NotNull ItemStackAccess stack,
            @Nullable EquipmentSlot slot,
            @Nullable EntityAccess wearer
    ) {
        return ItemAccessImpl.getEquipmentSlot(lethal$self(), stack, slot, wearer);
    }

    public int itemAccess$getArmorProtectionPoints(
            @NotNull ItemStackAccess stack,
            @Nullable EquipmentSlot slot,
            @Nullable EntityAccess wearer
    ) {
        return ItemAccessImpl.getArmorProtectionPoints(lethal$self(), stack, slot);
    }

    public double itemAccess$getArmorToughnessPoints(
            @NotNull ItemStackAccess stack,
            @Nullable EquipmentSlot slot,
            @Nullable EntityAccess wearer
    ) {
        return ItemAccessImpl.getArmorToughnessPoints(lethal$self(), stack, slot);
    }

    @Unique
    private Item lethal$self() {
        return (Item) (Object) this;
    }
}
