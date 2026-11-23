/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageFeature
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.asView
import heckerpowered.math.VectorView
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes

/**
 * Adapts native damage sources for bridge consumers. The direct entity is the immediate source of
 * the hit; the causing entity is the entity to which the host attributes it. Either entity and
 * the world-space origin may be absent.
 */
object DamageSourceAccessImpl {
    @JvmStatic
    fun directEntity(source: DamageSource): EntityAccess? = source.directEntity?.asView()

    @JvmStatic
    fun causingEntity(source: DamageSource): EntityAccess? = source.entity?.asView()

    @JvmStatic
    fun position(source: DamageSource): VectorView? = source.sourcePosition?.asView()

    @JvmStatic
    fun type(source: DamageSource): Identifier {
        val location = source.typeHolder().unwrapKey()
            .orElseThrow { IllegalStateException("Damage type is not registered") }.location()
        if (location == DamageTypes.FELL_OUT_OF_WORLD.location()) return VanillaDamageType.FellOutOfWorld.identifier
        return location.asView()
    }

    @JvmStatic
    fun has(source: DamageSource, feature: DamageFeature): Boolean = when (feature) {
        DamageFeature.DamagesHelmet -> source.`is`(DamageTypeTags.DAMAGES_HELMET)
        DamageFeature.BypassesArmor -> source.`is`(DamageTypeTags.BYPASSES_ARMOR)
        DamageFeature.BypassesShield -> source.`is`(DamageTypeTags.BYPASSES_SHIELD)
        DamageFeature.BypassesInvulnerability -> source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)
        DamageFeature.BypassesEffects -> source.`is`(DamageTypeTags.BYPASSES_EFFECTS)
        DamageFeature.BypassesResistance -> source.`is`(DamageTypeTags.BYPASSES_RESISTANCE)
        DamageFeature.BypassesEnchantments -> source.`is`(DamageTypeTags.BYPASSES_ENCHANTMENTS)
        DamageFeature.BypassesWolfArmor -> source.`is`(DamageTypeTags.BYPASSES_WOLF_ARMOR)
        DamageFeature.Fire -> source.`is`(DamageTypeTags.IS_FIRE)
        DamageFeature.Projectile -> source.`is`(DamageTypeTags.IS_PROJECTILE)
        DamageFeature.Explosion -> source.`is`(DamageTypeTags.IS_EXPLOSION)
        DamageFeature.Fall -> source.`is`(DamageTypeTags.IS_FALL)
        DamageFeature.Drowning -> source.`is`(DamageTypeTags.IS_DROWNING)
        DamageFeature.Freezing -> source.`is`(DamageTypeTags.IS_FREEZING)
        DamageFeature.Lightning -> source.`is`(DamageTypeTags.IS_LIGHTNING)
        DamageFeature.PlayerAttack -> source.`is`(DamageTypeTags.IS_PLAYER_ATTACK)
        DamageFeature.WitchResistantTo -> source.`is`(DamageTypeTags.WITCH_RESISTANT_TO)
        DamageFeature.WitherImmuneTo -> source.`is`(DamageTypeTags.WITHER_IMMUNE_TO)
        DamageFeature.NoAnger -> source.`is`(DamageTypeTags.NO_ANGER)
        DamageFeature.NoImpact -> source.`is`(DamageTypeTags.NO_IMPACT)
        DamageFeature.NoKnockback -> source.`is`(DamageTypeTags.NO_KNOCKBACK)
        DamageFeature.AlwaysMostSignificantFall -> source.`is`(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL)
        DamageFeature.AlwaysKillsArmorStands -> source.`is`(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS)
        DamageFeature.CanBreakArmorStand -> source.`is`(DamageTypeTags.CAN_BREAK_ARMOR_STAND)
        DamageFeature.AlwaysTriggersSilverfish -> source.`is`(DamageTypeTags.ALWAYS_TRIGGERS_SILVERFISH)
        DamageFeature.AlwaysHurtsEnderDragons -> source.`is`(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS)
        DamageFeature.IgnitesArmorStands -> source.`is`(DamageTypeTags.IGNITES_ARMOR_STANDS)
        DamageFeature.BurnsArmorStands -> source.`is`(DamageTypeTags.BURNS_ARMOR_STANDS)
        DamageFeature.AvoidsGuardianThorns -> source.`is`(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
        DamageFeature.BurnFromStepping -> source.`is`(DamageTypeTags.BURN_FROM_STEPPING)
        DamageFeature.PanicEnvironmentalCauses -> source.`is`(DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES)
        DamageFeature.PanicCauses -> source.`is`(DamageTypeTags.PANIC_CAUSES)
        DamageFeature.MaceSmash, DamageFeature.SulfurCubeWithBlockImmuneTo -> false
    }
}
