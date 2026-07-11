/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.forEach

enum class MouseButton {
    Left,
    Right,
}

enum class InputAction {
    Press,
    Release,
}

class MouseButtonEvent(val button: MouseButton, val action: InputAction) {
    var isCanceled = false
        private set

    fun cancel() {
        isCanceled = true
    }
}

interface MouseButtonInputRule {
    fun onMouseButtonInput(event: MouseButtonEvent)
}

object ClientInput {
    fun handle(event: MouseButtonEvent) {
        RuleRegistry.forEach<MouseButtonInputRule> { rule ->
            rule.onMouseButtonInput(event)
        }
    }
}
