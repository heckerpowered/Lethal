/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity.damagesource

enum class DamageFeature {
    DamagesHelmet,

    BypassesArmor,
    BypassesShield,
    BypassesInvulnerability,
    BypassesEffects,
    BypassesResistance,
    BypassesEnchantments,
    BypassesWolfArmor,

    Fire,
    Projectile,
    Explosion,
    Fall,
    Drowning,
    Freezing,
    Lightning,
    MaceSmash,
    PlayerAttack,

    WitchResistantTo,
    WitherImmuneTo,
    SulfurCubeWithBlockImmuneTo,

    NoAnger,
    NoImpact,
    NoKnockback,

    AlwaysMostSignificantFall,
    AlwaysKillsArmorStands,
    CanBreakArmorStand,
    AlwaysTriggersSilverfish,
    AlwaysHurtsEnderDragons,

    IgnitesArmorStands,
    BurnsArmorStands,
    AvoidsGuardianThorns,

    BurnFromStepping,

    PanicEnvironmentalCauses,
    PanicCauses
}