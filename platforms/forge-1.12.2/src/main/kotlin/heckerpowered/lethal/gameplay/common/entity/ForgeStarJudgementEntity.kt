/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.lethal.gameplay.common.skill.StarJudgementEffect
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind
import heckerpowered.lethal.platform.interop.vector
import heckerpowered.lethal.platform.interop.world
import net.minecraft.entity.Entity
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.datasync.DataParameter
import net.minecraft.network.datasync.DataSerializers
import net.minecraft.network.datasync.EntityDataManager
import net.minecraft.world.World
import java.util.UUID

abstract class ForgeStarJudgementEntity(
    world: World,
    val kind: StarJudgementKind,
) : Entity(world) {
    private var ownerIdentifier: UUID? = null
    private var initialOwner: PlayerAccess? = null
    private var effect: StarJudgementEffect? = null

    init {
        preventEntitySpawning = true
        isImmuneToFire = true
        setSize(1.0F, 1.0F)
        setNoGravity(true)
    }

    fun setOwner(owner: PlayerAccess) {
        ownerIdentifier = owner.uuid
        initialOwner = owner
        effect = null
    }

    override fun entityInit() {
        dataManager.register(FuseTicks, InitialFuseTicks)
    }

    override fun onUpdate() {
        super.onUpdate()
        if (world.isRemote) return

        val effect = effect ?: createEffect().also { effect = it }
        effect.tick()
        dataManager.set(FuseTicks, effect.fuseTicks)

        if (effect.isComplete) {
            setDead()
        }
    }

    override fun canBeCollidedWith(): Boolean {
        return !isDead
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        compound.setInteger(FuseTag, dataManager.get(FuseTicks))
        ownerIdentifier?.let { ownerIdentifier -> compound.setUniqueId(OwnerTag, ownerIdentifier) }
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        dataManager.set(FuseTicks, compound.getInteger(FuseTag))
        ownerIdentifier = if (compound.hasUniqueId(OwnerTag)) compound.getUniqueId(OwnerTag) else null
        initialOwner = null
        effect = null
    }

    private fun createEffect(): StarJudgementEffect {
        return StarJudgementEffect(
            world = world.world(),
            ownerIdentifier = ownerIdentifier,
            owner = initialOwner,
            position = { positionVector.vector() },
            kind = kind,
            initialFuseTicks = dataManager.get(FuseTicks),
        )
    }

    private companion object {
        val FuseTicks: DataParameter<Int> = EntityDataManager.createKey(
            ForgeStarJudgementEntity::class.java,
            DataSerializers.VARINT,
        )

        const val InitialFuseTicks = 20 * 5
        const val FuseTag = "Fuse"
        const val OwnerTag = "Owner"
    }
}

class ForgeStandardStarJudgementEntity(world: World) : ForgeStarJudgementEntity(
    world,
    StarJudgementKind.Standard,
)

class ForgeEnhancedStarJudgementEntity(world: World) : ForgeStarJudgementEntity(
    world,
    StarJudgementKind.Enhanced,
)
