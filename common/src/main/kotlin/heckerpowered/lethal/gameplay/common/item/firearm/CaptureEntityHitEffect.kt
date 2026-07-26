/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.DeferredExperienceDropAccess
import heckerpowered.bridge.adapter.entity.DroppedItemAccess
import heckerpowered.bridge.adapter.entity.ExperienceOrbAccess
import heckerpowered.bridge.math.asPointBox
import heckerpowered.bridge.math.expandedBy

object CaptureEntityHitEffect : EntityHitEffect {
    private const val CAPTURE_RADIUS_BLOCKS = 6.0

    override fun apply(result: EntityDamageResult) {
        val target = result.livingTarget ?: return
        val world = result.player.world
        if (world.isClientSide) return

        val captureArea = target.position.asPointBox().expandedBy(CAPTURE_RADIUS_BLOCKS)
        val deferredExperienceDrops = if (target.health <= 0.0) target as? DeferredExperienceDropAccess else null
        deferredExperienceDrops?.sendDeferredExperienceTo(result.player)

        val (experienceOrbs, droppedItems) = world.getEntities(captureArea)
            .filter { it is ExperienceOrbAccess || it is DroppedItemAccess }
            .partition { it is ExperienceOrbAccess }
            .let { (experienceOrbs, droppedItems) -> experienceOrbs.map { it as ExperienceOrbAccess } to droppedItems.map { it as DroppedItemAccess } }
        // Some hosts update their spatial entity storage immediately when an entity moves or is
        // removed. Finish the lazy query before applying either mutation so its iterator stays valid.
        for (experienceOrb in experienceOrbs) {
            val experiencePoints = experienceOrb.experiencePoints
            experienceOrb.remove()
            result.player.addExperiencePoints(experiencePoints)
        }

        for (droppedItem in droppedItems) {
            droppedItem.makeImmediatelyCollectible()
            droppedItem.position = result.player.position
        }
    }
}
