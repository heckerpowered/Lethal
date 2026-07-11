/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.damagesource.*
import heckerpowered.bridge.adapter.entity.damagesource.DamageProvider
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.platform.interop.damageSource
import heckerpowered.lethal.platform.interop.entityOrNull
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.entity.projectile.EntityFireball
import net.minecraft.util.DamageSource
import net.minecraft.util.EntityDamageSource
import net.minecraft.util.EntityDamageSourceIndirect

class DamageProvider : DamageProvider {
    private fun vanilla(spec: VanillaDamageSourceSpec, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
        return when (spec.type) {
            VanillaDamageType.InFire -> DamageSource.IN_FIRE.damageSource()
            VanillaDamageType.LightningBolt -> DamageSource.LIGHTNING_BOLT.damageSource()
            VanillaDamageType.OnFire -> DamageSource.ON_FIRE.damageSource()
            VanillaDamageType.Lava -> DamageSource.LAVA.damageSource()
            VanillaDamageType.HotFloor -> DamageSource.HOT_FLOOR.damageSource()
            VanillaDamageType.InWall -> DamageSource.IN_WALL.damageSource()
            VanillaDamageType.Cramming -> DamageSource.CRAMMING.damageSource()
            VanillaDamageType.Drown -> DamageSource.DROWN.damageSource()
            VanillaDamageType.Starve -> DamageSource.STARVE.damageSource()
            VanillaDamageType.Cactus -> DamageSource.CACTUS.damageSource()
            VanillaDamageType.Fall -> DamageSource.FALL.damageSource()
            VanillaDamageType.FlyIntoWall -> DamageSource.FLY_INTO_WALL.damageSource()
            VanillaDamageType.FellOutOfWorld -> DamageSource.OUT_OF_WORLD.damageSource()
            VanillaDamageType.Generic -> DamageSource.GENERIC.damageSource()
            VanillaDamageType.Magic -> DamageSource.MAGIC.damageSource()
            VanillaDamageType.Wither -> DamageSource.WITHER.damageSource()
            VanillaDamageType.FallingAnvil -> DamageSource.ANVIL.damageSource()
            VanillaDamageType.FallingBlock -> DamageSource.FALLING_BLOCK.damageSource()
            VanillaDamageType.DragonBreath -> DamageSource.DRAGON_BREATH.damageSource()
            VanillaDamageType.Fireworks -> DamageSource.FIREWORKS.damageSource()
            VanillaDamageType.MobAttack -> DamageSource.causeMobDamage(causingEntity.entityOrNull() as? EntityLivingBase).damageSource()
            VanillaDamageType.MobProjectile -> EntityDamageSourceIndirect("mob", directEntity.entityOrNull(), causingEntity.entityOrNull()).setProjectile().damageSource()
            VanillaDamageType.PlayerAttack -> DamageSource.causePlayerDamage(causingEntity.entityOrNull() as? EntityPlayer).damageSource()
            VanillaDamageType.Arrow -> DamageSource.causeArrowDamage(directEntity.entityOrNull() as? EntityArrow, causingEntity.entityOrNull()).damageSource()
            VanillaDamageType.Fireball -> EntityDamageSourceIndirect("fireball", directEntity.entityOrNull() as? EntityFireball, causingEntity.entityOrNull()).setFireDamage().setProjectile().damageSource()
            VanillaDamageType.UnattributedFireball -> virtual(spec, directEntity, causingEntity, position)
            VanillaDamageType.Thrown -> DamageSource.causeThrownDamage(directEntity.entityOrNull(), causingEntity.entityOrNull()).damageSource()
            VanillaDamageType.IndirectMagic -> DamageSource.causeIndirectMagicDamage(directEntity.entityOrNull(), causingEntity.entityOrNull()).damageSource()
            VanillaDamageType.Thorns -> DamageSource.causeThornsDamage(causingEntity.entityOrNull()).damageSource()
            VanillaDamageType.Explosion -> explosion()
            VanillaDamageType.PlayerExplosion -> EntityDamageSource("explosion.player", causingEntity.entityOrNull()).setDifficultyScaled().setExplosion().damageSource()
            else -> virtual(spec, directEntity, causingEntity, position)
        }
    }

    override fun source(spec: DamageSourceSpec, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
        return when (spec) {
            is VanillaDamageSourceSpec -> vanilla(spec, directEntity, causingEntity, position)
            is VirtualDamageSourceSpec -> VirtualDamageProvider.source(spec, directEntity, causingEntity, position)
        }
    }

    private fun virtual(spec: VanillaDamageSourceSpec, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
        return VirtualDamageProvider.source(spec, directEntity, causingEntity, position)
    }

    private fun explosion(): DamageSourceView {
        val causingEntity: EntityLivingBase? = null
        return DamageSource.causeExplosionDamage(causingEntity).damageSource()
    }
}
