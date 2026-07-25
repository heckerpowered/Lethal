/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl;

import heckerpowered.bridge.adapter.block.BlockStateAccess;
import heckerpowered.bridge.adapter.entity.EntityAccess;
import heckerpowered.bridge.adapter.item.EquipmentSlot;
import heckerpowered.bridge.adapter.item.Hand;
import heckerpowered.bridge.adapter.item.ItemForm;
import heckerpowered.bridge.adapter.item.ItemInteractionResult;
import heckerpowered.bridge.adapter.item.ItemUseAnimation;
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess;
import heckerpowered.bridge.adapter.world.WorldAccess;
import heckerpowered.bridge.math.BlockDirection;
import heckerpowered.bridge.math.BlockPositionView;
import heckerpowered.bridge.math.VectorView;
import heckerpowered.bridge.resources.Identifier;
import heckerpowered.lethal.platform.interop.MixinInterop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemAccessImpl {
    private ItemAccessImpl() {
    }

    @NotNull
    public static Identifier identifier(@NotNull Item item) {
        return MixinInterop.requireAccess(BuiltInRegistries.ITEM.getKey(item), Identifier.class);
    }

    @NotNull
    public static ItemForm form(@NotNull Item item) {
        if (item instanceof PickaxeItem) {
            return ItemForm.Pickaxe.INSTANCE;
        }
        if (item instanceof AxeItem) {
            return ItemForm.Axe.INSTANCE;
        }
        if (item instanceof ShovelItem) {
            return ItemForm.Shovel.INSTANCE;
        }
        if (item instanceof HoeItem) {
            return ItemForm.Hoe.INSTANCE;
        }
        if (item instanceof SwordItem) {
            return ItemForm.Sword.INSTANCE;
        }
        if (item instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case HELMET -> ItemForm.Helmet.INSTANCE;
                case CHESTPLATE -> ItemForm.Chestplate.INSTANCE;
                case LEGGINGS -> ItemForm.Leggings.INSTANCE;
                case BOOTS -> ItemForm.Boots.INSTANCE;
                case BODY -> ItemForm.Regular.INSTANCE;
            };
        }

        return ItemForm.Regular.INSTANCE;
    }

    @NotNull
    public static ItemInteractionResult use(@NotNull Item item, @NotNull WorldAccess world, @NotNull EntityAccess user, @NotNull Hand hand) {
        final Entity nativeUser = entity(user);
        if (!(nativeUser instanceof Player player)) {
            return ItemInteractionResult.Pass;
        }

        final InteractionResultHolder<ItemStack> result = item.use(level(world), player, hand(hand));
        return interactionResult(result.getResult());
    }

    @NotNull
    public static ItemInteractionResult useOnBlock(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess user,
            @NotNull Hand hand,
            @NotNull BlockPositionView position,
            @NotNull BlockDirection face,
            @NotNull VectorView hitPosition
    ) {
        final Entity nativeUser = entity(user);
        if (!(nativeUser instanceof Player player)) {
            return ItemInteractionResult.Pass;
        }

        final BlockHitResult hit = new BlockHitResult(vector(hitPosition), direction(face), blockPosition(position), false);
        final UseOnContext context = new UseOnContext(level(world), player, hand(hand), stack(stack), hit);
        return interactionResult(item.useOn(context));
    }

    @NotNull
    public static ItemInteractionResult interactLivingEntity(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull EntityAccess user,
            @NotNull EntityAccess target,
            @NotNull Hand hand
    ) {
        final Entity nativeUser = entity(user);
        final Entity nativeTarget = entity(target);
        if (!(nativeUser instanceof Player player) || !(nativeTarget instanceof LivingEntity livingTarget)) {
            return ItemInteractionResult.Pass;
        }

        return interactionResult(item.interactLivingEntity(stack(stack), player, livingTarget, hand(hand)));
    }

    public static void onUseTick(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess user,
            int remainingUseTicks
    ) {
        final Entity nativeUser = entity(user);
        if (nativeUser instanceof LivingEntity livingUser) {
            item.onUseTick(level(world), livingUser, stack(stack), remainingUseTicks);
        }
    }

    @NotNull
    public static ItemStackAccess finishUsing(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess user
    ) {
        final Entity nativeUser = entity(user);
        if (!(nativeUser instanceof LivingEntity livingUser)) {
            return stack;
        }

        final ItemStack result = item.finishUsingItem(stack(stack), level(world), livingUser);
        return MixinInterop.requireAccess(result, ItemStackAccess.class);
    }

    public static boolean releaseUsing(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess user,
            int remainingUseTicks
    ) {
        final Entity nativeUser = entity(user);
        if (!(nativeUser instanceof LivingEntity livingUser)) {
            return false;
        }

        item.releaseUsing(stack(stack), level(world), livingUser, remainingUseTicks);
        return false;
    }

    public static void inventoryTick(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull EntityAccess owner,
            @Nullable Integer slotIndex,
            boolean isSelected
    ) {
        item.inventoryTick(stack(stack), level(world), entity(owner), slotIndex == null ? -1 : slotIndex, isSelected);
    }

    public static void onCrafted(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @Nullable EntityAccess player
    ) {
        final ItemStack nativeStack = stack(stack);
        final Level nativeWorld = level(world);
        if (player == null) {
            item.onCraftedPostProcess(nativeStack, nativeWorld);
            return;
        }

        final Entity nativePlayer = entity(player);
        if (nativePlayer instanceof Player craftingPlayer) {
            item.onCraftedBy(nativeStack, nativeWorld, craftingPlayer);
        }
    }

    @NotNull
    public static ItemUseAnimation getUseAnimation(@NotNull Item item, @NotNull ItemStackAccess stack) {
        return switch (item.getUseAnimation(stack(stack))) {
            case EAT -> ItemUseAnimation.Eat;
            case DRINK -> ItemUseAnimation.Drink;
            case BLOCK -> ItemUseAnimation.Block;
            case BOW -> ItemUseAnimation.Bow;
            case SPEAR -> ItemUseAnimation.Spear;
            case NONE, CROSSBOW, SPYGLASS, TOOT_HORN, BRUSH, CUSTOM -> ItemUseAnimation.None;
        };
    }

    public static int getUseDurationTicks(@NotNull Item item, @NotNull ItemStackAccess stack, @Nullable EntityAccess user) {
        final Entity nativeUser = user == null ? null : entity(user);
        return item.getUseDuration(stack(stack), nativeUser instanceof LivingEntity livingUser ? livingUser : null);
    }

    public static double getDestroySpeed(@NotNull Item item, @NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return item.getDestroySpeed(stack(stack), blockState(blockState));
    }

    public static boolean canHarvest(@NotNull Item item, @NotNull ItemStackAccess stack, @NotNull BlockStateAccess blockState) {
        return item.isCorrectToolForDrops(stack(stack), blockState(blockState));
    }

    public static int getMiningLevel(@NotNull Item item) {
        if (!(form(item) instanceof ItemForm.MiningTool) || !(item instanceof TieredItem tieredItem)) {
            return 0;
        }

        final Tier tier = tieredItem.getTier();
        if (tier == Tiers.NETHERITE || tier.getIncorrectBlocksForDrops().equals(BlockTags.INCORRECT_FOR_NETHERITE_TOOL)) {
            return 4;
        }
        if (tier == Tiers.DIAMOND || tier.getIncorrectBlocksForDrops().equals(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) {
            return 3;
        }
        if (tier == Tiers.IRON || tier.getIncorrectBlocksForDrops().equals(BlockTags.INCORRECT_FOR_IRON_TOOL)) {
            return 2;
        }
        if (tier == Tiers.STONE || tier.getIncorrectBlocksForDrops().equals(BlockTags.INCORRECT_FOR_STONE_TOOL)) {
            return 1;
        }

        return 0;
    }

    public static boolean mineBlock(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull WorldAccess world,
            @NotNull BlockStateAccess blockState,
            @NotNull BlockPositionView position,
            @NotNull EntityAccess miner
    ) {
        final Entity nativeMiner = entity(miner);
        if (!(nativeMiner instanceof LivingEntity livingMiner)) {
            return false;
        }

        return item.mineBlock(stack(stack), level(world), blockState(blockState), blockPosition(position), livingMiner);
    }

    public static boolean hurtEnemy(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull EntityAccess target,
            @NotNull EntityAccess attacker
    ) {
        final Entity nativeTarget = entity(target);
        final Entity nativeAttacker = entity(attacker);
        if (!(nativeTarget instanceof LivingEntity livingTarget) || !(nativeAttacker instanceof LivingEntity livingAttacker)) {
            return false;
        }

        return item.hurtEnemy(stack(stack), livingTarget, livingAttacker);
    }

    public static void postHurtEnemy(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @NotNull EntityAccess target,
            @NotNull EntityAccess attacker
    ) {
        final Entity nativeTarget = entity(target);
        final Entity nativeAttacker = entity(attacker);
        if (nativeTarget instanceof LivingEntity livingTarget && nativeAttacker instanceof LivingEntity livingAttacker) {
            item.postHurtEnemy(stack(stack), livingTarget, livingAttacker);
        }
    }

    @Nullable
    public static EquipmentSlot getEquipmentSlot(
            @NotNull Item item,
            @NotNull ItemStackAccess stack,
            @Nullable EquipmentSlot requestedSlot,
            @Nullable EntityAccess wearer
    ) {
        final ItemStack nativeStack = stack(stack);
        net.minecraft.world.entity.EquipmentSlot nativeSlot = item.getEquipmentSlot(nativeStack);
        if (nativeSlot == null && item instanceof ArmorItem armor) {
            nativeSlot = armor.getEquipmentSlot();
        }
        if (nativeSlot != null) {
            return equipmentSlotOrNull(nativeSlot);
        }

        if (requestedSlot == null || wearer == null) {
            return null;
        }

        final Entity nativeWearer = entity(wearer);
        final net.minecraft.world.entity.EquipmentSlot requestedNativeSlot = equipmentSlot(requestedSlot);
        if (nativeWearer instanceof LivingEntity livingWearer && item.canEquip(nativeStack, requestedNativeSlot, livingWearer)) {
            return requestedSlot;
        }

        return null;
    }

    public static int getArmorProtectionPoints(@NotNull Item item, @NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot) {
        if (item instanceof ArmorItem armor) {
            final EquipmentSlot armorSlot = equipmentSlotOrNull(armor.getEquipmentSlot());
            if (armorSlot == null) {
                return 0;
            }
            return slot == null || slot == armorSlot ? armor.getDefense() : 0;
        }

        return (int) attributeAmount(stack(stack), slot, Attributes.ARMOR);
    }

    public static double getArmorToughnessPoints(@NotNull Item item, @NotNull ItemStackAccess stack, @Nullable EquipmentSlot slot) {
        if (item instanceof ArmorItem armor) {
            final EquipmentSlot armorSlot = equipmentSlotOrNull(armor.getEquipmentSlot());
            if (armorSlot == null) {
                return 0.0;
            }
            return slot == null || slot == armorSlot ? armor.getToughness() : 0.0;
        }

        return attributeAmount(stack(stack), slot, Attributes.ARMOR_TOUGHNESS);
    }

    private static double attributeAmount(
            @NotNull ItemStack stack,
            @Nullable EquipmentSlot slot,
            @NotNull net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute
    ) {
        if (slot == null) {
            return 0.0;
        }

        final net.minecraft.world.entity.EquipmentSlot nativeSlot = equipmentSlot(slot);
        double amount = 0.0;
        for (final var entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.slot().test(nativeSlot)
                    && entry.attribute().equals(attribute)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                amount += entry.modifier().amount();
            }
        }
        return amount;
    }

    @NotNull
    private static ItemInteractionResult interactionResult(@NotNull InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.Success;
            case CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.Consume;
            case PASS -> ItemInteractionResult.Pass;
            case FAIL -> ItemInteractionResult.Fail;
        };
    }

    @NotNull
    private static InteractionHand hand(@NotNull Hand hand) {
        return switch (hand) {
            case Main -> InteractionHand.MAIN_HAND;
            case Off -> InteractionHand.OFF_HAND;
        };
    }

    @NotNull
    private static Direction direction(@NotNull BlockDirection direction) {
        return switch (direction) {
            case Down -> Direction.DOWN;
            case Up -> Direction.UP;
            case North -> Direction.NORTH;
            case South -> Direction.SOUTH;
            case West -> Direction.WEST;
            case East -> Direction.EAST;
        };
    }

    @Nullable
    private static EquipmentSlot equipmentSlotOrNull(@NotNull net.minecraft.world.entity.EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> EquipmentSlot.MainHand;
            case OFFHAND -> EquipmentSlot.OffHand;
            case FEET -> EquipmentSlot.Feet;
            case LEGS -> EquipmentSlot.Legs;
            case CHEST -> EquipmentSlot.Chest;
            case HEAD -> EquipmentSlot.Head;
            case BODY -> null;
        };
    }

    @NotNull
    private static net.minecraft.world.entity.EquipmentSlot equipmentSlot(@NotNull EquipmentSlot slot) {
        return switch (slot) {
            case MainHand -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OffHand -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case Feet -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case Legs -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case Chest -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case Head -> net.minecraft.world.entity.EquipmentSlot.HEAD;
        };
    }

    @NotNull
    private static ItemStack stack(@NotNull ItemStackAccess stack) {
        return MixinInterop.requireHost(stack, ItemStack.class);
    }

    @NotNull
    private static Level level(@NotNull WorldAccess world) {
        return MixinInterop.requireHost(world, Level.class);
    }

    @NotNull
    private static Entity entity(@NotNull EntityAccess entity) {
        return MixinInterop.requireHost(entity, Entity.class);
    }

    @NotNull
    private static BlockState blockState(@NotNull BlockStateAccess blockState) {
        return MixinInterop.requireHost(blockState, BlockState.class);
    }

    @NotNull
    private static BlockPos blockPosition(@NotNull BlockPositionView position) {
        if (position instanceof BlockPos nativePosition) {
            return nativePosition;
        }
        return new BlockPos(position.getX(), position.getY(), position.getZ());
    }

    @NotNull
    private static Vec3 vector(@NotNull VectorView vector) {
        if (vector instanceof Vec3 nativeVector) {
            return nativeVector;
        }
        return new Vec3(vector.getX(), vector.getY(), vector.getZ());
    }
}
