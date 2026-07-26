/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.effect.ParticleEffect
import heckerpowered.bridge.adapter.effect.VanillaParticle
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

fun ParticleEffect.asHost(): ParticleOptions {
    return when (particle) {
        VanillaParticle.ExplosionNormal, VanillaParticle.ExplosionLarge -> ParticleTypes.EXPLOSION
        VanillaParticle.ExplosionHuge -> ParticleTypes.EXPLOSION_EMITTER
        VanillaParticle.FireworksSpark -> ParticleTypes.FIREWORK
        VanillaParticle.WaterBubble -> ParticleTypes.BUBBLE
        VanillaParticle.WaterSplash -> ParticleTypes.SPLASH
        VanillaParticle.WaterWake -> ParticleTypes.FISHING
        VanillaParticle.Suspended, VanillaParticle.SuspendedDepth -> ParticleTypes.UNDERWATER
        VanillaParticle.CriticalHit -> ParticleTypes.CRIT
        VanillaParticle.MagicCriticalHit -> ParticleTypes.ENCHANTED_HIT
        VanillaParticle.SmokeNormal -> ParticleTypes.SMOKE
        VanillaParticle.SmokeLarge -> ParticleTypes.LARGE_SMOKE
        VanillaParticle.Spell -> ParticleTypes.EFFECT
        VanillaParticle.InstantSpell -> ParticleTypes.INSTANT_EFFECT
        VanillaParticle.MobSpell -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0F, 1.0F, 1.0F)
        VanillaParticle.AmbientMobSpell -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x38FFFFFF)
        VanillaParticle.WitchSpell -> ParticleTypes.WITCH
        VanillaParticle.DrippingWater -> ParticleTypes.DRIPPING_WATER
        VanillaParticle.DrippingLava -> ParticleTypes.DRIPPING_LAVA
        VanillaParticle.AngryVillager -> ParticleTypes.ANGRY_VILLAGER
        VanillaParticle.HappyVillager -> ParticleTypes.HAPPY_VILLAGER
        VanillaParticle.TownAura -> ParticleTypes.MYCELIUM
        VanillaParticle.Note -> ParticleTypes.NOTE
        VanillaParticle.Portal -> ParticleTypes.PORTAL
        VanillaParticle.EnchantmentTable -> ParticleTypes.ENCHANT
        VanillaParticle.Flame -> ParticleTypes.FLAME
        VanillaParticle.Lava -> ParticleTypes.LAVA
        VanillaParticle.Footstep -> ParticleTypes.POOF
        VanillaParticle.Cloud -> ParticleTypes.CLOUD
        VanillaParticle.Redstone -> DustParticleOptions.REDSTONE
        VanillaParticle.Snowball -> ParticleTypes.ITEM_SNOWBALL
        VanillaParticle.SnowShovel -> ParticleTypes.SNOWFLAKE
        VanillaParticle.Slime -> ParticleTypes.ITEM_SLIME
        VanillaParticle.Heart -> ParticleTypes.HEART
        VanillaParticle.Barrier -> BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.BARRIER.defaultBlockState())
        VanillaParticle.ItemCrack -> itemParticle(data)
        VanillaParticle.BlockCrack, VanillaParticle.BlockDust -> blockParticle(ParticleTypes.BLOCK, data)
        VanillaParticle.WaterDrop -> ParticleTypes.RAIN
        VanillaParticle.ItemTake -> ParticleTypes.POOF
        VanillaParticle.MobAppearance -> ParticleTypes.ELDER_GUARDIAN
        VanillaParticle.DragonBreath -> ParticleTypes.DRAGON_BREATH
        VanillaParticle.EndRod -> ParticleTypes.END_ROD
        VanillaParticle.DamageIndicator -> ParticleTypes.DAMAGE_INDICATOR
        VanillaParticle.SweepAttack -> ParticleTypes.SWEEP_ATTACK
        VanillaParticle.FallingDust -> blockParticle(ParticleTypes.FALLING_DUST, data)
        VanillaParticle.Totem -> ParticleTypes.TOTEM_OF_UNDYING
        VanillaParticle.Spit -> ParticleTypes.SPIT
    }
}

private fun itemParticle(data: List<Int>): ParticleOptions {
    val itemIdentifier = data.first()
    val stack = ItemStack(Item.byId(itemIdentifier))
    require(!stack.isEmpty) { "Particle item ID does not identify an item: $itemIdentifier" }
    stack.damageValue = data[1]
    return ItemParticleOption(ParticleTypes.ITEM, stack)
}

private fun blockParticle(type: ParticleType<BlockParticleOption>, data: List<Int>): ParticleOptions {
    return BlockParticleOption(type, Block.stateById(data.first()))
}
