/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.VectorView
import heckerpowered.lethal.gameplay.common.entity.ForgeEnhancedStarJudgementEntity
import heckerpowered.lethal.gameplay.common.entity.ForgeStandardStarJudgementEntity
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind
import heckerpowered.lethal.gameplay.common.skill.StarJudgementSpawner
import heckerpowered.lethal.platform.interop.world

class ForgeStarJudgementSpawner : StarJudgementSpawner {
    override fun spawn(
        world: WorldAccess,
        owner: PlayerAccess,
        position: VectorView,
        kind: StarJudgementKind,
    ) {
        val nativeWorld = world.world()
        check(!nativeWorld.isRemote) { "Star Judgement must be spawned by an authoritative world" }

        val entity = when (kind) {
            StarJudgementKind.Standard -> ForgeStandardStarJudgementEntity(nativeWorld)
            StarJudgementKind.Enhanced -> ForgeEnhancedStarJudgementEntity(nativeWorld)
        }
        entity.setOwner(owner)
        entity.setPosition(position.x, position.y, position.z)
        nativeWorld.spawnEntity(entity)
    }
}
