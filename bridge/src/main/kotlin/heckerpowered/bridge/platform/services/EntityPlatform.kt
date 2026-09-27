/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.platform.services

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.adapter.entity.EntityBehavior
import heckerpowered.bridge.adapter.entity.EntityBlueprint
import heckerpowered.bridge.adapter.world.WorldAccess

/**
 * Materializes registered entity blueprints through the current host.
 */
interface EntityPlatform {
    fun create(world: WorldAccess, blueprint: EntityBlueprint, behavior: EntityBehavior = blueprint.createBehavior()): EntityAccess

    fun spawn(entity: EntityAccess): Boolean
}
