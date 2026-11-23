/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.MixinInterop
import heckerpowered.bridge.adapter.block.BlockAccess
import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.LivingEntityAccess
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.ServerPlayerAccess
import heckerpowered.bridge.adapter.entity.damagesource.*
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.gameplay.common.entity.damagesource.VirtualDamageSource
import net.minecraft.block.Block
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.DamageSource
import java.util.*
import heckerpowered.bridge.adapter.entity.damagesource.VirtualDamageSource as BridgeVirtualDamageSource

/**
 * Conversions used by legacy mixins when an operation crosses between host objects and bridge
 * views, including blocks and attributed damage sources.
 */
object ObjectInterop {
    @JvmStatic
    fun asView(block: Block): BlockAccess = MixinInterop.requireAccess<BlockAccess>(block)

    @JvmStatic
    fun asHost(source: DamageSourceView): DamageSource = source.asHost()

    @JvmStatic
    fun asView(source: DamageSource): DamageSourceView = source.asView()

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
        return vanillaType != null && vanillaType.has(feature) || when (feature) {
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

    @JvmStatic
    fun asView(entity: Entity): EntityAccess = entity.asView()

    @JvmStatic
    fun asViewOrNull(entity: Entity?): EntityAccess? = entity?.asView()

    @JvmStatic
    fun asHost(entity: EntityAccess): Entity = entity.asHost()

    @JvmStatic
    fun asHostOrNull(entity: EntityAccess?): Entity? = entity?.asHost()

    private fun damageSourceType(damageType: String): Identifier {
        require(damageType.isNotEmpty()) { "Damage type must not be empty" }

        val separator = damageType.indexOf(':')
        if (separator < 0) {
            return Identifier.create("minecraft", damageType)
        }

        require(separator > 0 && separator < damageType.lastIndex) { "Damage type must not have an empty namespace or path: $damageType" }
        require(damageType.indexOf(':', separator + 1) < 0) { "Damage type must contain at most one namespace separator: $damageType" }

        return Identifier.create(damageType.substring(0, separator), damageType.substring(separator + 1))
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

fun DamageSourceView.asHost(): DamageSource {
    if (this is DamageSource) return this

    val spec = toVirtualDamageSourceSpec()
    val nativeDirectEntity = directEntity?.asHost()
    val nativeCausingEntity = causingEntity?.asHost()
    val nativePosition = position?.asHost()
    return VirtualDamageSource(spec, nativeDirectEntity, nativeCausingEntity, nativePosition)
}

fun DamageSource.asView(): DamageSourceView = MixinInterop.requireAccess<DamageSourceView>(this)

fun DamageSource.damageSourceType(): Identifier {
    return ObjectInterop.damageSourceType(this)
}

fun DamageSource.hasDamageFeature(feature: DamageFeature): Boolean {
    return ObjectInterop.hasDamageFeature(this, feature)
}

fun Entity.asView(): EntityAccess = MixinInterop.requireAccess<EntityAccess>(this)
fun EntityLivingBase.asView(): LivingEntityAccess = MixinInterop.requireAccess<LivingEntityAccess>(this)
fun EntityPlayer.asView(): PlayerAccess = MixinInterop.requireAccess<PlayerAccess>(this)
fun EntityPlayerMP.asView(): ServerPlayerAccess = MixinInterop.requireAccess<ServerPlayerAccess>(this)

fun EntityAccess.asHost(): Entity = MixinInterop.requireHost<Entity>(this)

private fun DamageSourceView.toVirtualDamageSourceSpec(): VirtualDamageSourceSpec {
    return when (this) {
        is BridgeVirtualDamageSource -> VirtualDamageSourceSpec(spec.type, spec.features.clone())
        is QuantumVanillaDamageSource -> VirtualDamageSourceSpec(source.identifier, source.copyFeatures())
        else -> VirtualDamageSourceSpec(type, collectFeatures())
    }
}

private fun DamageSourceView.collectFeatures(): EnumSet<DamageFeature> {
    val features = EnumSet.noneOf(DamageFeature::class.java)
    DamageFeature.entries.filterTo(features) { has(it) }
    return features
}
