/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.entity

import heckerpowered.bridge.adapter.entity.EntityBlueprint
import heckerpowered.bridge.adapter.entity.EntityRegistry
import heckerpowered.lethal.gameplay.common.entity.starjudgement.EnhancedStarJudgement
import heckerpowered.lethal.gameplay.common.entity.starjudgement.StarJudgement
import heckerpowered.lethal.gameplay.common.entity.zeus.ZeusMissile

object ModEntities {
    init {
        register(StarJudgement)
        register(EnhancedStarJudgement)
        register(ZeusMissile)
    }

    fun onInitialize() {
    }

    private fun register(blueprint: EntityBlueprint) {
        EntityRegistry.register(blueprint)
    }
}
