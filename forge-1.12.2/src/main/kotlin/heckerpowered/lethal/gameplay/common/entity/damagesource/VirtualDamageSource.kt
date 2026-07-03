package heckerpowered.lethal.gameplay.common.entity.damagesource

import heckerpowered.lethal.bridge.adapter.entity.damagesource.DamageFeature
import heckerpowered.lethal.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.lethal.bridge.adapter.entity.damagesource.VirtualDamageSourceSpec
import net.minecraft.entity.Entity
import net.minecraft.util.EntityDamageSourceIndirect
import net.minecraft.util.math.Vec3d

class VirtualDamageSource(
    val spec: VirtualDamageSourceSpec,
    directEntity: Entity?,
    causingEntity: Entity?,
    val position: Vec3d?,
) : EntityDamageSourceIndirect(spec.legacyDamageTypeName(), directEntity, causingEntity) {
    init {
        applyFeatures(spec.features)
        applyLegacyVanillaFeatures()
    }

    override fun getDamageLocation(): Vec3d? {
        return position ?: super.getDamageLocation()
    }

    private fun applyFeatures(features: Set<DamageFeature>) {
        when {
            DamageFeature.BypassesArmor in features -> setDamageBypassesArmor()
            DamageFeature.BypassesInvulnerability in features -> setDamageAllowedInCreativeMode()
            DamageFeature.Fire in features -> setFireDamage()
            DamageFeature.Projectile in features -> setProjectile()
            DamageFeature.Explosion in features -> setExplosion()

            features.any(::isAbsoluteDamageFeature) -> setDamageIsAbsolute()
        }
    }

    private fun applyLegacyVanillaFeatures() {
        val vanillaType = VanillaDamageType.fromIdentifier(spec.type) ?: return
        when {
            vanillaType.isLegacyMagicDamage() -> setMagicDamage()
            vanillaType.isLegacyDifficultyScaled() -> setDifficultyScaled()
            vanillaType == VanillaDamageType.Thorns -> setIsThornsDamage()
        }
    }

    private fun isAbsoluteDamageFeature(feature: DamageFeature): Boolean {
        return feature == DamageFeature.BypassesEffects ||
                feature == DamageFeature.BypassesResistance ||
                feature == DamageFeature.BypassesEnchantments
    }

    private fun VanillaDamageType.isLegacyMagicDamage(): Boolean {
        return this == VanillaDamageType.Magic ||
                this == VanillaDamageType.IndirectMagic ||
                this == VanillaDamageType.Thorns
    }

    private fun VanillaDamageType.isLegacyDifficultyScaled(): Boolean {
        return this == VanillaDamageType.Explosion ||
                this == VanillaDamageType.PlayerExplosion ||
                this == VanillaDamageType.MobAttack ||
                this == VanillaDamageType.MobProjectile
    }

    private companion object {
        private fun VirtualDamageSourceSpec.legacyDamageTypeName(): String {
            val vanillaType = VanillaDamageType.fromIdentifier(type)
            if (vanillaType != null) {
                return vanillaType.legacyDamageTypeName
            }

            if (type.namespace == "minecraft") {
                return type.path
            }

            return type.asString()
        }

        private val VanillaDamageType.legacyDamageTypeName: String
            get() = when (this) {
                VanillaDamageType.InFire -> "inFire"
                VanillaDamageType.LightningBolt -> "lightningBolt"
                VanillaDamageType.OnFire -> "onFire"
                VanillaDamageType.Lava -> "lava"
                VanillaDamageType.HotFloor -> "hotFloor"
                VanillaDamageType.InWall -> "inWall"
                VanillaDamageType.Cramming -> "cramming"
                VanillaDamageType.Drown -> "drown"
                VanillaDamageType.Starve -> "starve"
                VanillaDamageType.Cactus -> "cactus"
                VanillaDamageType.Fall -> "fall"
                VanillaDamageType.FlyIntoWall -> "flyIntoWall"
                VanillaDamageType.FellOutOfWorld -> "outOfWorld"
                VanillaDamageType.Generic -> "generic"
                VanillaDamageType.Magic -> "magic"
                VanillaDamageType.Wither -> "wither"
                VanillaDamageType.FallingAnvil -> "anvil"
                VanillaDamageType.FallingBlock -> "fallingBlock"
                VanillaDamageType.DragonBreath -> "dragonBreath"
                VanillaDamageType.Fireworks -> "fireworks"
                VanillaDamageType.MobAttack -> "mob"
                VanillaDamageType.MobProjectile -> "mob"
                VanillaDamageType.PlayerAttack -> "player"
                VanillaDamageType.Arrow -> "arrow"
                VanillaDamageType.Fireball -> "fireball"
                VanillaDamageType.UnattributedFireball -> "onFire"
                VanillaDamageType.Thrown -> "thrown"
                VanillaDamageType.IndirectMagic -> "indirectMagic"
                VanillaDamageType.Thorns -> "thorns"
                VanillaDamageType.Explosion -> "explosion"
                VanillaDamageType.PlayerExplosion -> "explosion.player"

                VanillaDamageType.GenericKill -> "genericKill"
                VanillaDamageType.OutsideBorder -> "outsideBorder"
                VanillaDamageType.EnderPearl -> "enderPearl"
                VanillaDamageType.Stalagmite -> "stalagmite"
                VanillaDamageType.FallingStalactite -> "fallingStalactite"
                VanillaDamageType.Freeze -> "freeze"
                VanillaDamageType.SonicBoom -> "sonicBoom"
                VanillaDamageType.Trident -> "trident"
                VanillaDamageType.Spit -> "spit"
                VanillaDamageType.WindCharge -> "windCharge"
                VanillaDamageType.WitherSkull -> "witherSkull"
                VanillaDamageType.BadRespawnPoint -> "badRespawnPoint"
                VanillaDamageType.MobAttackNoAggro -> "mobAttackNoAggro"
                VanillaDamageType.Sting -> "sting"
                VanillaDamageType.Spear -> "spear"
                VanillaDamageType.MaceSmash -> "maceSmash"
                VanillaDamageType.DryOut -> "dryOut"
                VanillaDamageType.SweetBerryBush -> "sweetBerryBush"
                VanillaDamageType.Campfire -> "campfire"
            }
    }
}