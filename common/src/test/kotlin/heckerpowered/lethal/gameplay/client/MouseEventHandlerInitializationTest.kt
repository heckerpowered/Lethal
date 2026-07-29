/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.input.MouseButtonInputRule
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.all
import kotlin.test.Test
import kotlin.test.assertEquals

class MouseEventHandlerInitializationTest {
    @Test
    fun repeatedInitializationDoesNotRegisterDuplicateRules() {
        MouseEventHandler.onInitialize()
        MouseEventHandler.onInitialize()

        val registrations = RuleRegistry.all<MouseButtonInputRule>()
            .count { rule -> rule === MouseEventHandler }
        assertEquals(expected = 1, actual = registrations)
    }
}
