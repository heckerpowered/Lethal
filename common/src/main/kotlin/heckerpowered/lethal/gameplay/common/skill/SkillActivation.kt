/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.entity.EntityInterop
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.forEach

data class SkillActivationRequest(
    val player: PlayerAccess,
    val slot: SkillSlot,
)

interface SkillActivationRule {
    fun onSkillActivation(request: SkillActivationRequest)
}

object SkillActivation {
    fun handle(request: SkillActivationRequest) {
        if (!request.player.canActivateSkills()) return

        RuleRegistry.forEach<SkillActivationRule> { rule ->
            rule.onSkillActivation(request)
        }
    }
}

private fun PlayerAccess.canActivateSkills(): Boolean {
    if (!isAlive || isRemoved) return false
    return EntityInterop.spectator(this)?.isSpectator == false
}
