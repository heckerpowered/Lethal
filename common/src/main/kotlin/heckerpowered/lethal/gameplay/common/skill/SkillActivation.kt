/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill

import heckerpowered.bridge.adapter.entity.PlayerAccess
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
        if (!canActivate(request.player)) return

        RuleRegistry.forEach<SkillActivationRule> { rule ->
            rule.onSkillActivation(request)
        }
    }

    private fun canActivate(player: PlayerAccess): Boolean {
        return player.isAlive && !player.isRemoved && !player.isSpectator
    }
}
