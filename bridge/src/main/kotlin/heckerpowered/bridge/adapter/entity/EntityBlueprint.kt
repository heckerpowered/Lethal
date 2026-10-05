/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.entity

import heckerpowered.bridge.adapter.BridgeAccess
import heckerpowered.bridge.resources.Identifier

/**
 * Stable identity exposed by a registered host entity type.
 */
interface EntityTypeAccess : BridgeAccess {
    val identifier: Identifier
}

/**
 * Definition used by a host to materialize one registered entity type.
 *
 * The blueprint is a registered singleton, while each materialized entity is a distinct [EntityAccess]. Stateless
 * blueprints can implement [EntityBehavior] directly. A stateful blueprint must return a new behavior from
 * [createBehavior] for each materialized entity.
 */
interface EntityBlueprint : EntityTypeAccess, EntityBehavior {
    val properties: EntityProperties

    fun createBehavior(): EntityBehavior {
        return this
    }
}

/**
 * Behavior owned by one materialized entity.
 */
interface EntityBehavior {
    fun tick(entity: EntityAccess) {
    }

    fun isPickable(entity: EntityAccess): Boolean {
        return false
    }

    fun load(entity: EntityAccess, input: EntitySaveInput) {
    }

    fun save(entity: EntityAccess, output: EntitySaveOutput) {
    }
}

/**
 * Stable entity-type settings used when a host materializes an [EntityBlueprint].
 *
 * Older hosts may store some settings on each entity instance while newer hosts store them on the registered entity
 * type. The semantic ownership remains the entity type in either representation.
 */
data class EntityProperties(
    val dimensions: EntityDimensions,
    val tracking: EntityTracking,
    val isFireImmune: Boolean = false,
    val hasGravity: Boolean = true,
    val obstructsPlacement: Boolean = false,
    val isSerializable: Boolean = true,
    val isSummonable: Boolean = true,
)

data class EntityDimensions(
    val width: Float,
    val height: Float,
) {
    init {
        require(width.isFinite() && width >= 0.0F) { "Entity width must be finite and non-negative" }
        require(height.isFinite() && height >= 0.0F) { "Entity height must be finite and non-negative" }
    }
}

/**
 * Network tracking policy for an entity type.
 *
 * [rangeBlocks] uses physical blocks even when a host API expresses its tracking range in chunks.
 */
data class EntityTracking(
    val rangeBlocks: Int,
    val updateIntervalTicks: Int,
    val synchronizesVelocity: Boolean,
) {
    init {
        require(rangeBlocks >= 0) { "Entity tracking range must not be negative" }
        require(updateIntervalTicks > 0) { "Entity tracking update interval must be positive" }
    }
}
