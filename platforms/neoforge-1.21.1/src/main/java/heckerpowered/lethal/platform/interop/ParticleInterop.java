/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop;

import heckerpowered.bridge.adapter.effect.ParticleEffect;
import heckerpowered.bridge.adapter.effect.VanillaParticle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public final class ParticleInterop {
    private ParticleInterop() {
    }

    @NotNull
    public static ParticleOptions particle(@NotNull ParticleEffect effect) {
        final VanillaParticle particle = effect.getParticle();
        final java.util.List<Integer> data = effect.getData();
        return switch (particle) {
            case ExplosionNormal, ExplosionLarge -> ParticleTypes.EXPLOSION;
            case ExplosionHuge -> ParticleTypes.EXPLOSION_EMITTER;
            case FireworksSpark -> ParticleTypes.FIREWORK;
            case WaterBubble -> ParticleTypes.BUBBLE;
            case WaterSplash -> ParticleTypes.SPLASH;
            case WaterWake -> ParticleTypes.FISHING;
            case Suspended, SuspendedDepth -> ParticleTypes.UNDERWATER;
            case CriticalHit -> ParticleTypes.CRIT;
            case MagicCriticalHit -> ParticleTypes.ENCHANTED_HIT;
            case SmokeNormal -> ParticleTypes.SMOKE;
            case SmokeLarge -> ParticleTypes.LARGE_SMOKE;
            case Spell -> ParticleTypes.EFFECT;
            case InstantSpell -> ParticleTypes.INSTANT_EFFECT;
            case MobSpell -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0F, 1.0F, 1.0F);
            case AmbientMobSpell -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x38FFFFFF);
            case WitchSpell -> ParticleTypes.WITCH;
            case DrippingWater -> ParticleTypes.DRIPPING_WATER;
            case DrippingLava -> ParticleTypes.DRIPPING_LAVA;
            case AngryVillager -> ParticleTypes.ANGRY_VILLAGER;
            case HappyVillager -> ParticleTypes.HAPPY_VILLAGER;
            case TownAura -> ParticleTypes.MYCELIUM;
            case Note -> ParticleTypes.NOTE;
            case Portal -> ParticleTypes.PORTAL;
            case EnchantmentTable -> ParticleTypes.ENCHANT;
            case Flame -> ParticleTypes.FLAME;
            case Lava -> ParticleTypes.LAVA;
            case Footstep -> ParticleTypes.POOF;
            case Cloud -> ParticleTypes.CLOUD;
            case Redstone -> DustParticleOptions.REDSTONE;
            case Snowball -> ParticleTypes.ITEM_SNOWBALL;
            case SnowShovel -> ParticleTypes.SNOWFLAKE;
            case Slime -> ParticleTypes.ITEM_SLIME;
            case Heart -> ParticleTypes.HEART;
            case Barrier -> new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.BARRIER.defaultBlockState());
            case ItemCrack -> itemParticle(data);
            case BlockCrack, BlockDust -> blockParticle(ParticleTypes.BLOCK, data);
            case WaterDrop -> ParticleTypes.RAIN;
            case ItemTake -> ParticleTypes.POOF;
            case MobAppearance -> ParticleTypes.ELDER_GUARDIAN;
            case DragonBreath -> ParticleTypes.DRAGON_BREATH;
            case EndRod -> ParticleTypes.END_ROD;
            case DamageIndicator -> ParticleTypes.DAMAGE_INDICATOR;
            case SweepAttack -> ParticleTypes.SWEEP_ATTACK;
            case FallingDust -> blockParticle(ParticleTypes.FALLING_DUST, data);
            case Totem -> ParticleTypes.TOTEM_OF_UNDYING;
            case Spit -> ParticleTypes.SPIT;
        };
    }

    @NotNull
    private static ParticleOptions itemParticle(@NotNull java.util.List<Integer> data) {
        final ItemStack stack = new ItemStack(Item.byId(data.getFirst()));
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Particle item ID does not identify an item: " + data.getFirst());
        }
        stack.setDamageValue(data.get(1));
        return new ItemParticleOption(ParticleTypes.ITEM, stack);
    }

    @NotNull
    private static ParticleOptions blockParticle(
            @NotNull net.minecraft.core.particles.ParticleType<BlockParticleOption> type,
            @NotNull java.util.List<Integer> data
    ) {
        return new BlockParticleOption(type, Block.stateById(data.getFirst()));
    }
}
