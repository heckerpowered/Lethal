package heckerpowered.lethal.platform.services

import heckerpowered.lethal.bridge.adapter.entity.EntityAccess
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageProvider
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageSourceSpec
import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageSourceView
import heckerpowered.lethal.bridge.adapter.entity.damagesource.VanillaDamageSourceSpec
import heckerpowered.lethal.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.lethal.bridge.math.VectorView
import net.minecraft.util.DamageSource

class DamageProvider : DamageProvider {
    private fun vanilla(spec: VanillaDamageSourceSpec, directEntity: EntityAccess?, causingEntity: EntityAccess?, position: VectorView?): DamageSourceView {
        return when (spec.type) {
            VanillaDamageType.InFire -> native(DamageSource.IN_FIRE, spec, directEntity, causingEntity, position)
            VanillaDamageType.LightningBolt -> native(DamageSource.LIGHTNING_BOLT, spec, directEntity, causingEntity, position)
            VanillaDamageType.OnFire -> native(DamageSource.ON_FIRE, spec, directEntity, causingEntity, position)
            VanillaDamageType.Lava -> native(DamageSource.LAVA, spec, directEntity, causingEntity, position)
            VanillaDamageType.HotFloor -> native(DamageSource.HOT_FLOOR, spec, directEntity, causingEntity, position)
            VanillaDamageType.InWall -> native(DamageSource.IN_WALL, spec, directEntity, causingEntity, position)
            VanillaDamageType.Cramming -> native(DamageSource.CRAMMING, spec, directEntity, causingEntity, position)
            VanillaDamageType.Drown -> native(DamageSource.DROWN, spec, directEntity, causingEntity, position)
            VanillaDamageType.Starve -> native(DamageSource.STARVE, spec, directEntity, causingEntity, position)
            VanillaDamageType.Cactus -> native(DamageSource.CACTUS, spec, directEntity, causingEntity, position)
            VanillaDamageType.Fall -> native(DamageSource.FALL, spec, directEntity, causingEntity, position)
            VanillaDamageType.FlyIntoWall -> native(DamageSource.FLY_INTO_WALL, spec, directEntity, causingEntity, position)
            VanillaDamageType.FellOutOfWorld -> native(DamageSource.OUT_OF_WORLD, spec, directEntity, causingEntity, position)
            VanillaDamageType.Generic -> native(DamageSource.GENERIC, spec, directEntity, causingEntity, position)
            VanillaDamageType.Magic -> native(DamageSource.MAGIC, spec, directEntity, causingEntity, position)
            VanillaDamageType.Wither -> native(DamageSource.WITHER, spec, directEntity, causingEntity, position)
            VanillaDamageType.FallingAnvil -> native(DamageSource.ANVIL, spec, directEntity, causingEntity, position)
            VanillaDamageType.FallingBlock -> native(DamageSource.FALLING_BLOCK, spec, directEntity, causingEntity, position)
            VanillaDamageType.DragonBreath -> native(DamageSource.DRAGON_BREATH, spec, directEntity, causingEntity, position)
            VanillaDamageType.Fireworks -> native(DamageSource.FIREWORKS, spec, directEntity, causingEntity, position)

            VanillaDamageType.MobAttack -> native(
                DamageSource.causeMobDamage(causingEntity.nativeLivingEntity()),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.MobProjectile -> native(
                DamageSource.causeIndirectDamage(
                    directEntity.nativeEntity(),
                    causingEntity.nativeLivingEntity()
                ),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.PlayerAttack -> native(
                DamageSource.causePlayerDamage(causingEntity.nativePlayer()),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.Arrow -> native(
                DamageSource.causeArrowDamage(
                    directEntity.nativeArrow(),
                    causingEntity?.nativeEntityOrNull()
                ),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.Fireball -> native(
                DamageSource.causeFireballDamage(
                    directEntity.nativeFireball(),
                    causingEntity?.nativeEntityOrNull()
                ),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.UnattributedFireball -> native(
                DamageSource.causeFireballDamage(
                    directEntity.nativeFireball(),
                    null
                ),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.Thrown -> native(
                DamageSource.causeThrownDamage(
                    directEntity.nativeEntity(),
                    causingEntity?.nativeEntityOrNull()
                ),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.IndirectMagic -> native(
                DamageSource.causeIndirectMagicDamage(
                    directEntity.nativeEntity(),
                    causingEntity?.nativeEntityOrNull()
                ),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.Thorns -> native(
                DamageSource.causeThornsDamage(causingEntity.nativeEntity()),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.Explosion -> native(
                DamageSource.causeExplosionDamage(null as EntityLivingBase?),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.PlayerExplosion -> native(
                DamageSource.causeExplosionDamage(causingEntity.nativeLivingEntity()),
                spec,
                directEntity,
                causingEntity,
                position
            )

            VanillaDamageType.GenericKill,
            VanillaDamageType.OutsideBorder,
            VanillaDamageType.EnderPearl,
            VanillaDamageType.Stalagmite,
            VanillaDamageType.FallingStalactite,
            VanillaDamageType.Freeze,
            VanillaDamageType.SonicBoom,
            VanillaDamageType.Trident,
            VanillaDamageType.Spit,
            VanillaDamageType.WindCharge,
            VanillaDamageType.WitherSkull,
            VanillaDamageType.BadRespawnPoint,
            VanillaDamageType.MobAttackNoAggro,
            VanillaDamageType.Sting,
            VanillaDamageType.Spear,
            VanillaDamageType.MaceSmash,
            VanillaDamageType.DryOut,
            VanillaDamageType.SweetBerryBush,
                -> virtual(spec, directEntity, causingEntity, position)
        }
    }

    override fun source(
        spec: DamageSourceSpec,
        directEntity: EntityAccess?,
        causingEntity: EntityAccess?,
        position: VectorView?,
    ): DamageSourceView {
        return when (spec) {
            is VanillaDamageSourceSpec -> vanilla(spec, directEntity, causingEntity, position)
            is VirtualDamageSourceSpec -> VirtualDamageProvider.source(spec, directEntity, causingEntity, position)
            is NativeDamageSourceSpec -> native(spec, directEntity, causingEntity, position)
        }
    }

    private fun native(
        damageSource: DamageSource,
        spec: VanillaDamageSourceSpec,
        directEntity: EntityAccess?,
        causingEntity: EntityAccess?,
        position: VectorView?,
    ): DamageSourceView {
        return ForgeDamageSourceView(
            damageSource = damageSource,
            type = spec.type.identifier,
            directEntity = directEntity,
            causingEntity = causingEntity,
            position = position,
        )
    }

    private fun virtual(
        spec: VanillaDamageSourceSpec,
        directEntity: EntityAccess?,
        causingEntity: EntityAccess?,
        position: VectorView?,
    ): DamageSourceView {
        return VirtualDamageProvider.source(
            spec,
            directEntity,
            causingEntity,
            position
        )
    }

    private fun native(
        spec: NativeDamageSourceSpec,
        directEntity: EntityAccess?,
        causingEntity: EntityAccess?,
        position: VectorView?,
    ): DamageSourceView {
        error("Native damage source spec is not supported by this provider: $spec")
    }
}