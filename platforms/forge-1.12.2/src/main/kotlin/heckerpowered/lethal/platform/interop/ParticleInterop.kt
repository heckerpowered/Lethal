/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.effect.VanillaParticle
import net.minecraft.util.EnumParticleTypes

fun VanillaParticle.asHost(): EnumParticleTypes {
    return when (this) {
        VanillaParticle.ExplosionNormal -> EnumParticleTypes.EXPLOSION_NORMAL
        VanillaParticle.ExplosionLarge -> EnumParticleTypes.EXPLOSION_LARGE
        VanillaParticle.ExplosionHuge -> EnumParticleTypes.EXPLOSION_HUGE
        VanillaParticle.FireworksSpark -> EnumParticleTypes.FIREWORKS_SPARK
        VanillaParticle.WaterBubble -> EnumParticleTypes.WATER_BUBBLE
        VanillaParticle.WaterSplash -> EnumParticleTypes.WATER_SPLASH
        VanillaParticle.WaterWake -> EnumParticleTypes.WATER_WAKE
        VanillaParticle.Suspended -> EnumParticleTypes.SUSPENDED
        VanillaParticle.SuspendedDepth -> EnumParticleTypes.SUSPENDED_DEPTH
        VanillaParticle.CriticalHit -> EnumParticleTypes.CRIT
        VanillaParticle.MagicCriticalHit -> EnumParticleTypes.CRIT_MAGIC
        VanillaParticle.SmokeNormal -> EnumParticleTypes.SMOKE_NORMAL
        VanillaParticle.SmokeLarge -> EnumParticleTypes.SMOKE_LARGE
        VanillaParticle.Spell -> EnumParticleTypes.SPELL
        VanillaParticle.InstantSpell -> EnumParticleTypes.SPELL_INSTANT
        VanillaParticle.MobSpell -> EnumParticleTypes.SPELL_MOB
        VanillaParticle.AmbientMobSpell -> EnumParticleTypes.SPELL_MOB_AMBIENT
        VanillaParticle.WitchSpell -> EnumParticleTypes.SPELL_WITCH
        VanillaParticle.DrippingWater -> EnumParticleTypes.DRIP_WATER
        VanillaParticle.DrippingLava -> EnumParticleTypes.DRIP_LAVA
        VanillaParticle.AngryVillager -> EnumParticleTypes.VILLAGER_ANGRY
        VanillaParticle.HappyVillager -> EnumParticleTypes.VILLAGER_HAPPY
        VanillaParticle.TownAura -> EnumParticleTypes.TOWN_AURA
        VanillaParticle.Note -> EnumParticleTypes.NOTE
        VanillaParticle.Portal -> EnumParticleTypes.PORTAL
        VanillaParticle.EnchantmentTable -> EnumParticleTypes.ENCHANTMENT_TABLE
        VanillaParticle.Flame -> EnumParticleTypes.FLAME
        VanillaParticle.Lava -> EnumParticleTypes.LAVA
        VanillaParticle.Footstep -> EnumParticleTypes.FOOTSTEP
        VanillaParticle.Cloud -> EnumParticleTypes.CLOUD
        VanillaParticle.Redstone -> EnumParticleTypes.REDSTONE
        VanillaParticle.Snowball -> EnumParticleTypes.SNOWBALL
        VanillaParticle.SnowShovel -> EnumParticleTypes.SNOW_SHOVEL
        VanillaParticle.Slime -> EnumParticleTypes.SLIME
        VanillaParticle.Heart -> EnumParticleTypes.HEART
        VanillaParticle.Barrier -> EnumParticleTypes.BARRIER
        VanillaParticle.ItemCrack -> EnumParticleTypes.ITEM_CRACK
        VanillaParticle.BlockCrack -> EnumParticleTypes.BLOCK_CRACK
        VanillaParticle.BlockDust -> EnumParticleTypes.BLOCK_DUST
        VanillaParticle.WaterDrop -> EnumParticleTypes.WATER_DROP
        VanillaParticle.ItemTake -> EnumParticleTypes.ITEM_TAKE
        VanillaParticle.MobAppearance -> EnumParticleTypes.MOB_APPEARANCE
        VanillaParticle.DragonBreath -> EnumParticleTypes.DRAGON_BREATH
        VanillaParticle.EndRod -> EnumParticleTypes.END_ROD
        VanillaParticle.DamageIndicator -> EnumParticleTypes.DAMAGE_INDICATOR
        VanillaParticle.SweepAttack -> EnumParticleTypes.SWEEP_ATTACK
        VanillaParticle.FallingDust -> EnumParticleTypes.FALLING_DUST
        VanillaParticle.Totem -> EnumParticleTypes.TOTEM
        VanillaParticle.Spit -> EnumParticleTypes.SPIT
    }
}
