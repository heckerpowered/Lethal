/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientInputTest {
    @BeforeTest
    fun setUp() {
        clearMouseButtonInputRules()
    }

    @AfterTest
    fun tearDown() {
        clearMouseButtonInputRules()
    }

    @Test
    fun registeredRuleReceivesMouseButtonEventsInOrder() {
        val receivedEvents = mutableListOf<MouseButtonEvent>()
        RuleRegistry.register<MouseButtonInputRule>(RecordingMouseButtonInputRule(receivedEvents))

        val leftPress = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        val rightRelease = MouseButtonEvent(MouseButton.Right, InputAction.Release)
        ClientInput.handle(leftPress)
        ClientInput.handle(rightRelease)

        assertEquals(listOf(leftPress, rightRelease), receivedEvents)
    }

    @Test
    fun allRegisteredRulesReceiveMouseButtonEvents() {
        val firstRuleEvents = mutableListOf<MouseButtonEvent>()
        val secondRuleEvents = mutableListOf<MouseButtonEvent>()
        RuleRegistry.register<MouseButtonInputRule>(RecordingMouseButtonInputRule(firstRuleEvents))
        RuleRegistry.register<MouseButtonInputRule>(RecordingMouseButtonInputRule(secondRuleEvents))

        val event = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        ClientInput.handle(event)

        assertEquals(listOf(event), firstRuleEvents)
        assertEquals(listOf(event), secondRuleEvents)
    }

    @Test
    fun canceledEventRemainsCanceledForFollowingRules() {
        var followingRuleObservedCancellation = false
        val cancelingRule = object : MouseButtonInputRule {
            override fun onMouseButtonInput(event: MouseButtonEvent) {
                event.cancel()
            }
        }
        val followingRule = object : MouseButtonInputRule {
            override fun onMouseButtonInput(event: MouseButtonEvent) {
                followingRuleObservedCancellation = event.isCanceled
            }
        }
        RuleRegistry.register<MouseButtonInputRule>(cancelingRule)
        RuleRegistry.register<MouseButtonInputRule>(followingRule)

        val event = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        ClientInput.handle(event)

        assertTrue(event.isCanceled)
        assertTrue(followingRuleObservedCancellation)
    }

    private fun clearMouseButtonInputRules() {
        RuleRegistry.rules.remove(MouseButtonInputRule::class.java)
    }

    private class RecordingMouseButtonInputRule(private val receivedEvents: MutableList<MouseButtonEvent>) : MouseButtonInputRule {
        override fun onMouseButtonInput(event: MouseButtonEvent) {
            receivedEvents += event
        }
    }
}
