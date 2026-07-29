/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.DamageProvider
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceSpec
import heckerpowered.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageSourceSpec
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.entity.damagesource.VirtualDamageProvider
import heckerpowered.bridge.adapter.entity.damagesource.VirtualDamageSourceSpec
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.gameplay.common.entity.damagesource.attributedDamageSource
import heckerpowered.lethal.platform.interop.asHost
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.entity.projectile.EntityFireball
import net.minecraft.util.DamageSource
import net.minecraft.util.EntityDamageSource
import net.minecraft.util.EntityDamageSourceIndirect

class ForgeDamageProvider : DamageProvider {
    override fun source(spec: DamageSourceSpec, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
        return when (spec) {
            is VanillaDamageSourceSpec -> ForgeDamageSources.source(spec, directEntity?.asHost(), causingEntity?.asHost(), position)
            is VirtualDamageSourceSpec -> VirtualDamageProvider.source(spec, directEntity, causingEntity, position)
        }
    }
}

internal object ForgeDamageSources {
    fun source(spec: VanillaDamageSourceSpec, directEntity: Entity?, causingEntity: Entity?, position: VectorView? = null): DamageSourceView {
        val nativeSource = native(spec, directEntity, causingEntity) ?: return virtual(spec, directEntity, causingEntity, position)
        if (directEntity == null && causingEntity == null && position == null) {
            return nativeSource.asView()
        }

        return attributedDamageSource(spec, nativeSource, directEntity, causingEntity, position)
    }

    private fun native(spec: VanillaDamageSourceSpec, directEntity: Entity?, causingEntity: Entity?): DamageSource? {
        return when (spec.type) {
            VanillaDamageType.InFire -> DamageSource.IN_FIRE
            VanillaDamageType.LightningBolt -> DamageSource.LIGHTNING_BOLT
            VanillaDamageType.OnFire -> DamageSource.ON_FIRE
            VanillaDamageType.Lava -> DamageSource.LAVA
            VanillaDamageType.HotFloor -> DamageSource.HOT_FLOOR
            VanillaDamageType.InWall -> DamageSource.IN_WALL
            VanillaDamageType.Cramming -> DamageSource.CRAMMING
            VanillaDamageType.Drown -> DamageSource.DROWN
            VanillaDamageType.Starve -> DamageSource.STARVE
            VanillaDamageType.Cactus -> DamageSource.CACTUS
            VanillaDamageType.Fall -> DamageSource.FALL
            VanillaDamageType.FlyIntoWall -> DamageSource.FLY_INTO_WALL
            VanillaDamageType.FellOutOfWorld -> DamageSource.OUT_OF_WORLD
            VanillaDamageType.Generic -> DamageSource.GENERIC
            VanillaDamageType.Magic -> DamageSource.MAGIC
            VanillaDamageType.Wither -> DamageSource.WITHER
            VanillaDamageType.FallingAnvil -> DamageSource.ANVIL
            VanillaDamageType.FallingBlock -> DamageSource.FALLING_BLOCK
            VanillaDamageType.DragonBreath -> DamageSource.DRAGON_BREATH
            VanillaDamageType.Fireworks -> DamageSource.FIREWORKS
            VanillaDamageType.MobAttack -> DamageSource.causeMobDamage(causingEntity as? EntityLivingBase)
            VanillaDamageType.MobProjectile -> EntityDamageSourceIndirect("mob", directEntity, causingEntity).setProjectile()
            VanillaDamageType.PlayerAttack -> DamageSource.causePlayerDamage(causingEntity as? EntityPlayer)
            VanillaDamageType.Arrow -> DamageSource.causeArrowDamage(directEntity as? EntityArrow, causingEntity)
            VanillaDamageType.Fireball -> EntityDamageSourceIndirect("fireball", directEntity as? EntityFireball, causingEntity).setFireDamage().setProjectile()
            VanillaDamageType.UnattributedFireball -> null
            VanillaDamageType.Thrown -> DamageSource.causeThrownDamage(directEntity, causingEntity)
            VanillaDamageType.IndirectMagic -> DamageSource.causeIndirectMagicDamage(directEntity, causingEntity)
            VanillaDamageType.Thorns -> DamageSource.causeThornsDamage(causingEntity)
            VanillaDamageType.Explosion -> explosion()
            VanillaDamageType.PlayerExplosion -> EntityDamageSource("explosion.player", causingEntity).setDifficultyScaled().setExplosion()
            else -> null
        }
    }

    private fun virtual(spec: VanillaDamageSourceSpec, directEntity: Entity?, causingEntity: Entity?, position: VectorView?): DamageSourceView {
        return VirtualDamageProvider.source(spec, directEntity?.asView(), causingEntity?.asView(), position)
    }

    private fun explosion(): DamageSource {
        val causingEntity: EntityLivingBase? = null
        return DamageSource.causeExplosionDamage(causingEntity)
    }
}
