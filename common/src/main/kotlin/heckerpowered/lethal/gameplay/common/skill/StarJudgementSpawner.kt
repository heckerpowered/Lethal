/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.math.VectorView
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.platform.load

/**
 * Creates the host entity that owns a persistent Star Judgement effect.
 */
fun interface StarJudgementSpawner {
    fun spawn(world: WorldAccess, owner: PlayerAccess, position: VectorView, kind: StarJudgementKind)
}

internal object HostingStarJudgementSpawner : StarJudgementSpawner {
    override fun spawn(world: WorldAccess, owner: PlayerAccess, position: VectorView, kind: StarJudgementKind) {
        Services.load<StarJudgementSpawner>().spawn(world, owner, position, kind)
    }
}
