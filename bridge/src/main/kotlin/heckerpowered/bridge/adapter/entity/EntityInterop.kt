/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

/**
 * Resolves entity subtypes and optional capabilities at a single boundary.
 *
 * Hosts normally expose a capability through their entity adapter. Keeping resolution here lets
 * gameplay code remain independent from that representation and leaves one place for a future
 * fallback strategy.
 */
object EntityInterop {
    @JvmStatic
    fun deferredExperienceDrops(entity: EntityAccess?): DeferredExperienceDropAccess? {
        return entity as? DeferredExperienceDropAccess
    }

    @JvmStatic
    fun equipment(entity: EntityAccess?): EntityEquipmentAccess? {
        return entity as? EntityEquipmentAccess
    }

    @JvmStatic
    fun execution(entity: EntityAccess?): EntityExecutionAccess? {
        return entity as? EntityExecutionAccess
    }

    @JvmStatic
    fun removal(entity: EntityAccess?): EntityRemovalAccess? {
        return entity as? EntityRemovalAccess
    }

    @JvmStatic
    fun experienceOrb(entity: EntityAccess?): ExperienceOrbAccess? {
        return entity as? ExperienceOrbAccess
    }

    @JvmStatic
    fun experienceReceiver(entity: EntityAccess?): ExperienceReceiverAccess? {
        return entity as? ExperienceReceiverAccess
    }

    @JvmStatic
    fun glowing(entity: EntityAccess?): GlowingAccess? {
        return entity as? GlowingAccess
    }

    @JvmStatic
    fun droppedItem(entity: EntityAccess?): DroppedItemAccess? {
        return entity as? DroppedItemAccess
    }

    @JvmStatic
    fun living(entity: EntityAccess?): LivingEntityAccess? {
        return entity as? LivingEntityAccess
    }

    @JvmStatic
    fun multipart(entity: EntityAccess?): MultipartEntityAccess? {
        return entity as? MultipartEntityAccess
    }

    @JvmStatic
    fun part(entity: EntityAccess?): EntityPartAccess? {
        return entity as? EntityPartAccess
    }

    @JvmStatic
    fun serverPlayer(entity: EntityAccess?): ServerPlayerAccess? {
        return entity as? ServerPlayerAccess
    }

    @JvmStatic
    fun spectator(entity: EntityAccess?): SpectatorAccess? {
        return entity as? SpectatorAccess
    }
}
