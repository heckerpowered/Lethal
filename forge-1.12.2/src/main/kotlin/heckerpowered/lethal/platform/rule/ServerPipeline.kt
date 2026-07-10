/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.rule

import heckerpowered.lethal.bridge.rule.RuleRegistry
import heckerpowered.lethal.bridge.rule.ServerUpdateRule
import heckerpowered.lethal.bridge.rule.forEach

object ServerPipeline {
    @JvmStatic
    fun onUpdate() {
        RuleRegistry.forEach<ServerUpdateRule> { it.onServerUpdate() }
    }
}
