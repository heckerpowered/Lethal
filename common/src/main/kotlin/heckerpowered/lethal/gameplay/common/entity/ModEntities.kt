/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity

import heckerpowered.bridge.adapter.entity.EntityBlueprint
import heckerpowered.bridge.adapter.entity.EntityRegistry

object ModEntities {
    init {
        register(StarJudgement)
        register(EnhancedStarJudgement)
    }

    fun onInitialize() {
    }

    private fun register(blueprint: EntityBlueprint) {
        EntityRegistry.register(blueprint)
    }
}
