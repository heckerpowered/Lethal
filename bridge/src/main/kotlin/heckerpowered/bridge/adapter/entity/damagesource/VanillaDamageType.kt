/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity.damagesource

import heckerpowered.bridge.resources.Identifier
import java.util.*

enum class VanillaDamageType(path: String, private val assignedFeatures: EnumSet<DamageFeature>) {
    Generic(
        "generic",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.NoKnockback
        )
    ),

    GenericKill(
        "generic_kill",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesInvulnerability,
            DamageFeature.BypassesResistance,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.NoKnockback
        )
    ),

    FellOutOfWorld(
        "fell_out_of_world",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesInvulnerability,
            DamageFeature.BypassesResistance,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.AlwaysMostSignificantFall,
            DamageFeature.NoKnockback
        )
    ),

    OutsideBorder(
        "outside_border",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.NoKnockback
        )
    ),

    InFire(
        "in_fire",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.Fire,
            DamageFeature.IgnitesArmorStands,
            DamageFeature.NoKnockback,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses
        )
    ),

    OnFire(
        "on_fire",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.Fire,
            DamageFeature.BurnsArmorStands,
            DamageFeature.NoKnockback,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses
        )
    ),

    Lava(
        "lava",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.Fire,
            DamageFeature.NoKnockback,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses
        )
    ),

    HotFloor(
        "hot_floor",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.Fire,
            DamageFeature.BurnFromStepping,
            DamageFeature.NoKnockback,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses
        )
    ),

    Campfire(
        "campfire",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.Fire,
            DamageFeature.BurnFromStepping,
            DamageFeature.IgnitesArmorStands,
            DamageFeature.NoKnockback
        )
    ),

    DryOut(
        "dry_out",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.NoKnockback
        )
    ),

    SweetBerryBush(
        "sweet_berry_bush",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.NoKnockback
        )
    ),

    InWall(
        "in_wall",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.NoKnockback
        )
    ),

    Cramming(
        "cramming",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.NoKnockback
        )
    ),

    Cactus(
        "cactus",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    FlyIntoWall(
        "fly_into_wall",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.NoKnockback
        )
    ),

    Fall(
        "fall",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.Fall,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.NoKnockback
        )
    ),

    EnderPearl(
        "ender_pearl",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.Fall,
            DamageFeature.NoKnockback
        )
    ),

    Stalagmite(
        "stalagmite",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.Fall,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.NoKnockback
        )
    ),

    FallingBlock(
        "falling_block",
        features(
            DamageFeature.DamagesHelmet,
            DamageFeature.SulfurCubeWithBlockImmuneTo
        )
    ),

    FallingAnvil(
        "falling_anvil",
        features(
            DamageFeature.DamagesHelmet,
            DamageFeature.BypassesShield,
            DamageFeature.SulfurCubeWithBlockImmuneTo
        )
    ),

    FallingStalactite(
        "falling_stalactite",
        features(
            DamageFeature.DamagesHelmet,
            DamageFeature.BypassesShield,
            DamageFeature.SulfurCubeWithBlockImmuneTo
        )
    ),

    Drown(
        "drown",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.Drowning,
            DamageFeature.NoImpact,
            DamageFeature.WitherImmuneTo,
            DamageFeature.NoKnockback
        )
    ),

    Starve(
        "starve",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesEffects,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.NoKnockback
        )
    ),

    Freeze(
        "freeze",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.Freezing,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    Magic(
        "magic",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.WitchResistantTo,
            DamageFeature.AvoidsGuardianThorns,
            DamageFeature.AlwaysTriggersSilverfish,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    IndirectMagic(
        "indirect_magic",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.WitchResistantTo,
            DamageFeature.PanicCauses
        )
    ),

    Wither(
        "wither",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesWolfArmor,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    DragonBreath(
        "dragon_breath",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    SonicBoom(
        "sonic_boom",
        features(
            DamageFeature.BypassesArmor,
            DamageFeature.BypassesShield,
            DamageFeature.BypassesEnchantments,
            DamageFeature.WitchResistantTo,
            DamageFeature.PanicCauses
        )
    ),

    LightningBolt(
        "lightning_bolt",
        features(
            DamageFeature.BypassesShield,
            DamageFeature.Lightning,
            DamageFeature.PanicEnvironmentalCauses,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    Arrow(
        "arrow",
        features(
            DamageFeature.Projectile,
            DamageFeature.AlwaysKillsArmorStands,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Trident(
        "trident",
        features(
            DamageFeature.Projectile,
            DamageFeature.AlwaysKillsArmorStands,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Thrown(
        "thrown",
        features(
            DamageFeature.Projectile,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    MobProjectile(
        "mob_projectile",
        features(
            DamageFeature.Projectile,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Spit(
        "spit",
        features(DamageFeature.SulfurCubeWithBlockImmuneTo)
    ),

    WindCharge(
        "wind_charge",
        features(
            DamageFeature.Projectile,
            DamageFeature.AlwaysKillsArmorStands,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Fireball(
        "fireball",
        features(
            DamageFeature.Fire,
            DamageFeature.Projectile,
            DamageFeature.AlwaysKillsArmorStands,
            DamageFeature.PanicCauses
        )
    ),

    UnattributedFireball(
        "unattributed_fireball",
        features(
            DamageFeature.Fire,
            DamageFeature.Projectile,
            DamageFeature.PanicCauses
        )
    ),

    WitherSkull(
        "wither_skull",
        features(
            DamageFeature.Projectile,
            DamageFeature.AlwaysKillsArmorStands,
            DamageFeature.PanicCauses
        )
    ),

    Fireworks(
        "fireworks",
        features(
            DamageFeature.Explosion,
            DamageFeature.AlwaysHurtsEnderDragons,
            DamageFeature.AvoidsGuardianThorns,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Explosion(
        "explosion",
        features(
            DamageFeature.Explosion,
            DamageFeature.AlwaysHurtsEnderDragons,
            DamageFeature.AvoidsGuardianThorns,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    PlayerExplosion(
        "player_explosion",
        features(
            DamageFeature.Explosion,
            DamageFeature.AlwaysHurtsEnderDragons,
            DamageFeature.AvoidsGuardianThorns,
            DamageFeature.CanBreakArmorStand,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    BadRespawnPoint(
        "bad_respawn_point",
        features(
            DamageFeature.Explosion,
            DamageFeature.AlwaysHurtsEnderDragons,
            DamageFeature.AvoidsGuardianThorns,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.NoKnockback
        )
    ),

    MobAttack(
        "mob_attack",
        features(
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    MobAttackNoAggro(
        "mob_attack_no_aggro",
        features(
            DamageFeature.NoAnger,
            DamageFeature.SulfurCubeWithBlockImmuneTo
        )
    ),

    PlayerAttack(
        "player_attack",
        features(
            DamageFeature.PlayerAttack,
            DamageFeature.CanBreakArmorStand,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Thorns(
        "thorns",
        features(
            DamageFeature.WitchResistantTo,
            DamageFeature.AvoidsGuardianThorns,
            DamageFeature.BypassesWolfArmor
        )
    ),

    Sting(
        "sting",
        features(
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    ),

    Spear(
        "spear",
        features(
            DamageFeature.PlayerAttack,
            DamageFeature.CanBreakArmorStand,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses,
            DamageFeature.NoKnockback
        )
    ),

    MaceSmash(
        "mace_smash",
        features(
            DamageFeature.MaceSmash,
            DamageFeature.PlayerAttack,
            DamageFeature.CanBreakArmorStand,
            DamageFeature.SulfurCubeWithBlockImmuneTo,
            DamageFeature.PanicCauses
        )
    );

    val identifier: Identifier = Identifier.create("minecraft", path)

    val path: String
        get() = identifier.path

    fun has(feature: DamageFeature): Boolean =
        feature in assignedFeatures

    fun copyFeatures(): EnumSet<DamageFeature> =
        assignedFeatures.clone()

    fun toVirtualDamageType(): VirtualDamageType =
        VirtualDamageType(identifier, copyFeatures())

    companion object {
        private val valuesByPath: Map<String, VanillaDamageType> =
            entries.associateBy(VanillaDamageType::path)

        private val valuesByIdentifier: Map<Identifier, VanillaDamageType> =
            entries.associateBy(VanillaDamageType::identifier)

        fun fromPath(path: String): VanillaDamageType? =
            valuesByPath[path]

        fun fromIdentifier(identifier: Identifier): VanillaDamageType? =
            valuesByIdentifier[identifier]
    }
}
