/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform

import heckerpowered.lethal.gameplay.common.entity.ForgeStarJudgementEntity
import heckerpowered.lethal.gameplay.common.skill.StarJudgementEntityAccess
import heckerpowered.lethal.gameplay.common.skill.StarJudgementKind

class StarJudgementEntityAccessor(
    private val starJudgementEntity: ForgeStarJudgementEntity,
) : EntityAccessor(starJudgementEntity), StarJudgementEntityAccess {
    override val starJudgementKind: StarJudgementKind
        get() = starJudgementEntity.kind
}
