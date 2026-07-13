/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.adapter.client.render.ClientPostProcessContext
import heckerpowered.bridge.adapter.client.render.ClientPostProcessRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register

object BloomTest : ClientPostProcessRule {
    var Enabled = true
    var BrightnessThreshold = 0.7F
        set(value) {
            require(value.isFinite()) { "Bloom brightness threshold must be finite" }
            field = value
        }

    fun onInitialize() {
        RuleRegistry.register<ClientPostProcessRule>(this)
    }

    override fun onPostProcess(context: ClientPostProcessContext) {
        if (!Enabled || !context.isBloomSupported) return
        BrightnessThreshold = 0.5F
        context.applyBloomToScene(BrightnessThreshold)
    }
}
