/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.entity.damagesource.*
import heckerpowered.lethal.bridge.resources.Identifier
import heckerpowered.lethal.gameplay.common.entity.damagesource.VirtualDamageSource
import net.minecraft.entity.Entity
import net.minecraft.util.DamageSource
import java.util.*
import heckerpowered.lethal.bridge.adapter.entity.damagesource.VirtualDamageSource as BridgeVirtualDamageSource

object ObjectInterop {
    fun source(source: DamageSourceView): DamageSource {
        return damageSource(source)
    }

    @JvmStatic
    fun damageSource(source: DamageSourceView): DamageSource {
        if (source is DamageSource) return source

        return VirtualDamageSource(
            spec = source.toVirtualDamageSourceSpec(),
            directEntity = entityOrNull(source.directEntity),
            causingEntity = entityOrNull(source.causingEntity),
            position = source.position?.let { GeometryInterop.vector(it) }
        )
    }

    @JvmStatic
    fun damageSource(source: DamageSource): DamageSourceView {
        return source as DamageSourceView
    }

    @JvmStatic
    fun damageSourceType(source: DamageSource): Identifier {
        if (source is VirtualDamageSource) return source.spec.type

        val vanillaType = vanillaDamageType(source)
        if (vanillaType != null) return vanillaType.identifier

        return damageSourceType(source.damageType)
    }

    @JvmStatic
    fun hasDamageFeature(source: DamageSource, feature: DamageFeature): Boolean {
        val vanillaType = vanillaDamageType(source)
        if (vanillaType != null && vanillaType.has(feature)) return true

        return when (feature) {
            DamageFeature.BypassesArmor -> source.isUnblockable
            DamageFeature.BypassesInvulnerability -> source.canHarmInCreative()
            DamageFeature.BypassesEffects,
            DamageFeature.BypassesResistance,
            DamageFeature.BypassesEnchantments,
                -> source.isDamageAbsolute

            DamageFeature.Fire -> source.isFireDamage
            DamageFeature.Projectile -> source.isProjectile
            DamageFeature.Explosion -> source.isExplosion
            DamageFeature.WitchResistantTo -> source.isMagicDamage
            else -> false
        }
    }

    private fun DamageSourceView.toVirtualDamageSourceSpec(): VirtualDamageSourceSpec {
        return when (this) {
            is BridgeVirtualDamageSource -> spec.copy(features = spec.features.clone())
            is QuantumVanillaDamageSource -> VirtualDamageSourceSpec(source.identifier, source.copyFeatures())
            else -> VirtualDamageSourceSpec(type, collectFeatures())
        }
    }

    private fun DamageSourceView.collectFeatures(): EnumSet<DamageFeature> {
        val features = EnumSet.noneOf(DamageFeature::class.java)
        DamageFeature.entries.filterTo(features) { has(it) }
        return features
    }

    @JvmStatic
    fun entity(entity: Entity): EntityAccess {
        return entity as? EntityAccess ?: EntityAccessor(entity)
    }

    @JvmStatic
    fun entityOrNull(entity: Entity?): EntityAccess? {
        if (entity == null) return null
        return entity(entity)
    }

    @JvmStatic
    fun entity(entity: EntityAccess): Entity {
        return when (entity) {
            is Entity -> entity
            is EntityAccessor -> entity.entity
            else -> error("Unsupported entity access implementation: ${entity::class.java.name}")
        }
    }

    @JvmStatic
    fun entityOrNull(entity: EntityAccess?): Entity? {
        if (entity == null) return null
        return entity(entity)
    }

    private fun damageSourceType(damageType: String): Identifier {
        require(damageType.isNotEmpty()) { "Damage type must not be empty" }

        val separator = damageType.indexOf(':')
        if (separator < 0) {
            return Identifier.create("minecraft", damageType)
        }

        require(separator > 0 && separator < damageType.lastIndex) {
            "Damage type must not have an empty namespace or path: $damageType"
        }

        require(damageType.indexOf(':', separator + 1) < 0) {
            "Damage type must contain at most one namespace separator: $damageType"
        }

        return Identifier.create(
            damageType.substring(0, separator),
            damageType.substring(separator + 1)
        )
    }

    private fun vanillaDamageType(source: DamageSource): VanillaDamageType? {
        return when (source.damageType) {
            "inFire" -> VanillaDamageType.InFire
            "lightningBolt" -> VanillaDamageType.LightningBolt
            "onFire" -> VanillaDamageType.OnFire
            "lava" -> VanillaDamageType.Lava
            "hotFloor" -> VanillaDamageType.HotFloor
            "inWall" -> VanillaDamageType.InWall
            "cramming" -> VanillaDamageType.Cramming
            "drown" -> VanillaDamageType.Drown
            "starve" -> VanillaDamageType.Starve
            "cactus" -> VanillaDamageType.Cactus
            "fall" -> VanillaDamageType.Fall
            "flyIntoWall" -> VanillaDamageType.FlyIntoWall
            "outOfWorld" -> VanillaDamageType.FellOutOfWorld
            "generic" -> VanillaDamageType.Generic
            "magic" -> VanillaDamageType.Magic
            "wither" -> VanillaDamageType.Wither
            "anvil" -> VanillaDamageType.FallingAnvil
            "fallingBlock" -> VanillaDamageType.FallingBlock
            "dragonBreath" -> VanillaDamageType.DragonBreath
            "fireworks" -> VanillaDamageType.Fireworks
            "mob" -> if (source.isProjectile) VanillaDamageType.MobProjectile else VanillaDamageType.MobAttack
            "player" -> VanillaDamageType.PlayerAttack
            "arrow" -> VanillaDamageType.Arrow
            "fireball" -> VanillaDamageType.Fireball
            "thrown" -> VanillaDamageType.Thrown
            "indirectMagic" -> VanillaDamageType.IndirectMagic
            "thorns" -> VanillaDamageType.Thorns
            "explosion" -> VanillaDamageType.Explosion
            "explosion.player" -> VanillaDamageType.PlayerExplosion
            else -> null
        }
    }
}
