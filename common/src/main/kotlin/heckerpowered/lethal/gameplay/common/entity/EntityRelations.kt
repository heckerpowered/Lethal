/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.OwnedEntityAccess

/**
 * Identifies allies relative to an entity from native ownership and alliance relationships.
 * A false result does not classify the target as hostile.
 */
object EntityRelations {
    fun isFriendlyTo(target: EntityAccess, reference: EntityAccess): Boolean {
        if (target.uuid == reference.uuid) return true

        val targetOwner = (target as? OwnedEntityAccess)?.ownerUuid
        val referenceOwner = (reference as? OwnedEntityAccess)?.ownerUuid
        if (targetOwner == reference.uuid || referenceOwner == target.uuid) return true
        if (targetOwner != null && targetOwner == referenceOwner) return true

        return target.isAlliedTo(reference) || reference.isAlliedTo(target)
    }
}
