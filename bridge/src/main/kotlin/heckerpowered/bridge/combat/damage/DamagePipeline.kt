/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.combat.damage

import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.all
import heckerpowered.bridge.rule.forEach

object DamagePipeline {
    @JvmStatic
    fun attempt(context: DamageAttemptContext) {
        for (rule in RuleRegistry.all<DamageAttemptRule>()) {
            rule.onAttempt(context)
            if (context.isCancelled) return
        }
    }

    @JvmStatic
    fun computation(context: DamageComputationContext) {
        for (rule in RuleRegistry.all<DamageComputationRule>()) {
            rule.onComputation(context)
            if (context.isCancelled) return
        }
    }

    @JvmStatic
    fun realization(context: DamageRealizationContext) {
        RuleRegistry.forEach<DamageRealizationRule> { it.onRealization(context) }
    }

    @JvmStatic
    fun outcome(context: DamageOutcomeContext) {
        RuleRegistry.forEach<DamageOutcomeRule> { it.onOutcome(context) }
    }

    @JvmStatic
    fun settlement(context: DamageSettlementContext) {
        RuleRegistry.forEach<DamageSettlementRule> { it.onSettlement(context) }
    }
}

