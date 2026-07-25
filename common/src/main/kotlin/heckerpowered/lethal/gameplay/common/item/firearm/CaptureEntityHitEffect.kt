/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.DroppedItemAccess
import heckerpowered.bridge.adapter.entity.EntityInterop
import heckerpowered.bridge.adapter.entity.ExperienceOrbAccess
import heckerpowered.bridge.math.expandedBy

object CaptureEntityHitEffect : EntityHitEffect {
    private const val CAPTURE_RADIUS_BLOCKS = 6.0

    override fun apply(result: EntityDamageResult) {
        val target = result.livingTarget ?: return
        val world = result.player.world
        if (world.isClientSide) return

        val captureArea = target.boundingBox.expandedBy(CAPTURE_RADIUS_BLOCKS)
        val experienceReceiver = EntityInterop.experienceReceiver(result.player)
        val deferredExperienceDrops = if (target.health <= 0.0) {
            EntityInterop.deferredExperienceDrops(target)
        } else {
            null
        }
        if (deferredExperienceDrops != null) {
            val receiver = experienceReceiver
                ?: error("Capture requires experience receiver access for player ${result.player.id}")
            deferredExperienceDrops.sendDeferredExperienceTo(receiver)
        }

        val experienceOrbs = mutableListOf<ExperienceOrbAccess>()
        val droppedItems = mutableListOf<DroppedItemAccess>()
        for (entity in world.getEntities(captureArea)) {
            val experienceOrb = EntityInterop.experienceOrb(entity)
            if (experienceOrb != null) {
                experienceOrbs += experienceOrb
                continue
            }

            val droppedItem = EntityInterop.droppedItem(entity) ?: continue
            droppedItems += droppedItem
        }

        /*
         * Some hosts update their spatial entity storage immediately when an entity moves or is
         * removed. Finish the lazy query before applying either mutation so its iterator stays valid.
         */
        if (experienceOrbs.isNotEmpty()) {
            val receiver = experienceReceiver
                ?: error("Capture requires experience receiver access for player ${result.player.id}")
            for (experienceOrb in experienceOrbs) {
                val experiencePoints = experienceOrb.experiencePoints
                experienceOrb.consume()
                receiver.addExperiencePoints(experiencePoints)
            }
        }

        for (droppedItem in droppedItems) {
            droppedItem.makeImmediatelyCollectible()
            droppedItem.position = result.player.position
        }
    }
}
