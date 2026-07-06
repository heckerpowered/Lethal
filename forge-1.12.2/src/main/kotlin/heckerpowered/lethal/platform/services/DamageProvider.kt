/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.entity.damagesource.*
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageProvider
import heckerpowered.lethal.bridge.math.VectorView
import heckerpowered.lethal.platform.interop.ObjectInterop
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
            VanillaDamageType.InFire -> ObjectInterop.damageSource(DamageSource.IN_FIRE)
            VanillaDamageType.LightningBolt -> ObjectInterop.damageSource(DamageSource.LIGHTNING_BOLT)
            VanillaDamageType.OnFire -> ObjectInterop.damageSource(DamageSource.ON_FIRE)
            VanillaDamageType.Lava -> ObjectInterop.damageSource(DamageSource.LAVA)
            VanillaDamageType.HotFloor -> ObjectInterop.damageSource(DamageSource.HOT_FLOOR)
            VanillaDamageType.InWall -> ObjectInterop.damageSource(DamageSource.IN_WALL)
            VanillaDamageType.Cramming -> ObjectInterop.damageSource(DamageSource.CRAMMING)
            VanillaDamageType.Drown -> ObjectInterop.damageSource(DamageSource.DROWN)
            VanillaDamageType.Starve -> ObjectInterop.damageSource(DamageSource.STARVE)
            VanillaDamageType.Cactus -> ObjectInterop.damageSource(DamageSource.CACTUS)
            VanillaDamageType.Fall -> ObjectInterop.damageSource(DamageSource.FALL)
            VanillaDamageType.FlyIntoWall -> ObjectInterop.damageSource(DamageSource.FLY_INTO_WALL)
            VanillaDamageType.FellOutOfWorld -> ObjectInterop.damageSource(DamageSource.OUT_OF_WORLD)
            VanillaDamageType.Generic -> ObjectInterop.damageSource(DamageSource.GENERIC)
            VanillaDamageType.Magic -> ObjectInterop.damageSource(DamageSource.MAGIC)
            VanillaDamageType.Wither -> ObjectInterop.damageSource(DamageSource.WITHER)
            VanillaDamageType.FallingAnvil -> ObjectInterop.damageSource(DamageSource.ANVIL)
            VanillaDamageType.FallingBlock -> ObjectInterop.damageSource(DamageSource.FALLING_BLOCK)
            VanillaDamageType.DragonBreath -> ObjectInterop.damageSource(DamageSource.DRAGON_BREATH)
            VanillaDamageType.Fireworks -> ObjectInterop.damageSource(DamageSource.FIREWORKS)

            VanillaDamageType.MobAttack -> ObjectInterop.damageSource(
                DamageSource.causeMobDamage(ObjectInterop.entityOrNull(causingEntity) as? EntityLivingBase)
            )

            VanillaDamageType.MobProjectile -> ObjectInterop.damageSource(
                EntityDamageSourceIndirect(
                    "mob",
                    ObjectInterop.entityOrNull(directEntity),
                    ObjectInterop.entityOrNull(causingEntity)
                ).setProjectile()
            )

            VanillaDamageType.PlayerAttack -> ObjectInterop.damageSource(
                DamageSource.causePlayerDamage(ObjectInterop.entityOrNull(causingEntity) as? EntityPlayer)
            )

            VanillaDamageType.Arrow -> ObjectInterop.damageSource(
                DamageSource.causeArrowDamage(
                    ObjectInterop.entityOrNull(directEntity) as? EntityArrow,
                    ObjectInterop.entityOrNull(causingEntity)
                )
            )

            VanillaDamageType.Fireball -> ObjectInterop.damageSource(
                EntityDamageSourceIndirect(
                    "fireball",
                    ObjectInterop.entityOrNull(directEntity) as? EntityFireball,
                    ObjectInterop.entityOrNull(causingEntity)
                ).setFireDamage().setProjectile()
            )

            VanillaDamageType.UnattributedFireball -> virtual(spec, directEntity, causingEntity, position)

            VanillaDamageType.Thrown -> ObjectInterop.damageSource(
                DamageSource.causeThrownDamage(
                    ObjectInterop.entityOrNull(directEntity),
                    ObjectInterop.entityOrNull(causingEntity)
                )
            )

            VanillaDamageType.IndirectMagic -> ObjectInterop.damageSource(
                DamageSource.causeIndirectMagicDamage(ObjectInterop.entityOrNull(directEntity), ObjectInterop.entityOrNull(causingEntity))
            )

            VanillaDamageType.Thorns -> ObjectInterop.damageSource(DamageSource.causeThornsDamage(ObjectInterop.entityOrNull(causingEntity)))

            VanillaDamageType.Explosion -> ObjectInterop.damageSource(
                @Suppress("USELESS_CAST")
                DamageSource.causeExplosionDamage(null as? EntityLivingBase)
            )

            VanillaDamageType.PlayerExplosion -> ObjectInterop.damageSource(
                EntityDamageSource(
                    "explosion.player",
                    ObjectInterop.entityOrNull(causingEntity)
                ).setDifficultyScaled().setExplosion()
            )

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
}
