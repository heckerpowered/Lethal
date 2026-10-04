/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.combat

import heckerpowered.bridge.combat.damage.DamageAttemptContext
import heckerpowered.bridge.combat.damage.DamageAttemptRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.lethal.gameplay.common.item.ZeusArmor

object ZeusArmorDamageRule : DamageAttemptRule {
    fun onInitialize() {
        RuleRegistry.register<DamageAttemptRule>(this)
    }

    override fun onAttempt(context: DamageAttemptContext) {
        if (ZeusArmor.preventsDamage(context.target, context.source.type)) {
            context.cancel()
        }
    }
}
