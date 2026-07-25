/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.EntityAccess

/**
 * A host entity carrying a persistent Star Judgement effect.
 */
interface StarJudgementEntityAccess : EntityAccess {
    val starJudgementKind: StarJudgementKind
}

/** Resolves Star Judgement entities at the gameplay boundary. */
object StarJudgementEntityInterop {
    fun entity(entity: EntityAccess?): StarJudgementEntityAccess? {
        return entity as? StarJudgementEntityAccess
    }
}
