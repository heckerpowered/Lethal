/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.input.MouseButton
import heckerpowered.bridge.input.MouseButtonEvent
import heckerpowered.bridge.input.MouseButtonInputRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register

object MouseEventHandler : MouseButtonInputRule {
    init {
        RuleRegistry.register<MouseButtonInputRule>(this)
    }

    fun onInitialize() {
    }

    override fun onMouseButtonInput(event: MouseButtonEvent) {
        if (event.button != MouseButton.Left) return

        
    }
}