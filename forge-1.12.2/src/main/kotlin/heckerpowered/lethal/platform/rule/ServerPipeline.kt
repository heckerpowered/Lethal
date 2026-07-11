/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.rule

import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.ServerUpdateRule
import heckerpowered.bridge.rule.forEach

object ServerPipeline {
    @JvmStatic
    fun onUpdate() {
        RuleRegistry.forEach<ServerUpdateRule> { it.onServerUpdate() }
    }
}
