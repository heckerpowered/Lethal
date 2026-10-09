/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.PlayerInventoryAccess
import heckerpowered.bridge.adapter.entity.damagesource.VanillaDamageType
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.world.raycast.EntityRayHit
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.entity.athena.AthenaGrenade
import heckerpowered.lethal.gameplay.common.item.firearm.EntityHitDamage
import heckerpowered.lethal.gameplay.common.item.firearm.GunFireMode
import heckerpowered.lethal.gameplay.common.item.firearm.RegularGun
import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import heckerpowered.lethal.gameplay.common.skill.SkillWeapon
import heckerpowered.lethal.gameplay.common.skill.WeaponSkill
import heckerpowered.lethal.gameplay.common.sound.ModSounds
import heckerpowered.math.times

open class Athena protected constructor(
    identifierPath: String,
    bulletDamagePoints: Double,
    private val grenadeDamagePoints: Double,
) : RegularGun(), SkillWeapon {
    companion object : Athena("athena", 50.0, 17.0) {
        private val FiringFrequency = Frequency.perSecond(2)
        private val ProjectileModeKey = Constants.identifier("athena_projectile_mode")
        private val AresIdentifier = Constants.identifier("ares")
        private val AresGoldenIdentifier = Constants.identifier("ares_golden")

        fun getProjectileMode(weaponStack: ItemStackAccess): AthenaProjectileMode {
            return weaponStack.input
                .decode(ProjectileModeKey, AthenaProjectileMode.serializer()) ?: AthenaProjectileMode.Bullet
        }
    }

    final override val identifier: Identifier = Constants.identifier(identifierPath)

    private val bulletDamage = EntityHitDamage(VanillaDamageType.Generic, bulletDamagePoints)
    private val modeSwitch = object : WeaponSkill {
        override fun activate(player: PlayerAccess, weaponStack: ItemStackAccess) {
            val nextMode = when (getProjectileMode(weaponStack)) {
                AthenaProjectileMode.Bullet -> AthenaProjectileMode.Grenade
                AthenaProjectileMode.Grenade -> AthenaProjectileMode.Bullet
            }
            weaponStack.output.encode(ProjectileModeKey, AthenaProjectileMode.serializer(), nextMode)
        }
    }

    override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency = FiringFrequency

    override fun getFireMode(player: PlayerAccess, weaponStack: ItemStackAccess): GunFireMode {
        val inventory = player as? PlayerInventoryAccess ?: return GunFireMode.SemiAutomatic
        val carriesAres = inventory.carriedStacks.any { stack ->
            !stack.isEmpty && (stack.item.identifier == AresIdentifier || stack.item.identifier == AresGoldenIdentifier)
        }
        return if (carriesAres) GunFireMode.Automatic else GunFireMode.SemiAutomatic
    }

    override fun getSkill(slot: SkillSlot): WeaponSkill? = if (slot == SkillSlot.Primary) modeSwitch else null

    override fun getRayTraceDistanceBlocks(player: PlayerAccess, weaponStack: ItemStackAccess): Double = 15.0

    override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        if (getProjectileMode(weaponStack) == AthenaProjectileMode.Bullet) {
            super.shoot(player, weaponStack, shotCount)
            return
        }

        val direction = player.viewVector
        val magnitude = direction.length
        val origin = player.eyePosition
        if (!magnitude.isFinite() || magnitude <= 0.0) return
        if (!origin.x.isFinite() || !origin.y.isFinite() || !origin.z.isFinite()) return

        if (shotCount > 0L) player.world.playSound(origin, ModSounds.ZeusFire)
        for (shotIndex in 0L until shotCount) {
            val grenade = Services.EntityPlatform.create(player.world, AthenaGrenade, AthenaGrenade.createBehavior(player, grenadeDamagePoints))
            grenade.position = origin
            grenade.velocity = direction * (AthenaGrenade.SPEED_BLOCKS_PER_TICK / magnitude)
            grenade.pitch = player.pitch
            grenade.yaw = player.yaw
            Services.EntityPlatform.spawn(grenade)
        }
    }

    override fun onRayTrace(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long, entityHits: Sequence<EntityRayHit>) {
        if (shotCount > 0L) player.world.playSound(player.eyePosition, ModSounds.ZeusFire)
        bulletDamage.apply(player, weaponStack, shotCount, entityHits.take(1))
    }

}
