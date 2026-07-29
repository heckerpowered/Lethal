/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.input

import heckerpowered.bridge.resources.IdentifierProvider
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import kotlin.test.*

class ClientInputTest {
    @BeforeTest
    fun setUp() {
        clearMouseButtonInputRules()
        clearKeyBindingInputRules()
    }

    @AfterTest
    fun tearDown() {
        clearMouseButtonInputRules()
        clearKeyBindingInputRules()
    }

    @Test
    fun registeredRuleReceivesMouseButtonEventsInOrder() {
        val receivedEvents = mutableListOf<MouseButtonEvent>()
        RuleRegistry.register<MouseButtonInputRule>(RecordingMouseButtonInputRule(receivedEvents))

        val leftPress = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        val rightRelease = MouseButtonEvent(MouseButton.Right, InputAction.Release)
        ClientInput.handle(leftPress)
        ClientInput.handle(rightRelease)

        assertEquals(expected = listOf(leftPress, rightRelease), actual = receivedEvents)
    }

    @Test
    fun allRegisteredRulesReceiveMouseButtonEvents() {
        val firstRuleEvents = mutableListOf<MouseButtonEvent>()
        val secondRuleEvents = mutableListOf<MouseButtonEvent>()
        RuleRegistry.register<MouseButtonInputRule>(RecordingMouseButtonInputRule(firstRuleEvents))
        RuleRegistry.register<MouseButtonInputRule>(RecordingMouseButtonInputRule(secondRuleEvents))

        val event = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        ClientInput.handle(event)

        assertEquals(expected = listOf(event), actual = firstRuleEvents)
        assertEquals(expected = listOf(event), actual = secondRuleEvents)
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

    @Test
    fun registeredRuleReceivesKeyBindingPressedEvents() {
        val receivedEvents = mutableListOf<KeyBindingPressedEvent>()
        val rule = object : KeyBindingInputRule {
            override fun onKeyBindingInput(event: KeyBindingPressedEvent) {
                receivedEvents += event
            }
        }
        RuleRegistry.register<KeyBindingInputRule>(rule)
        val event = KeyBindingPressedEvent(
            IdentifierProvider.Freestanding.identifier("lethal", "skill/primary"),
        )

        ClientInput.handle(event)

        assertEquals(expected = listOf(event), actual = receivedEvents)
    }

    private fun clearMouseButtonInputRules() {
        RuleRegistry.rules.remove(MouseButtonInputRule::class.java)
    }

    private fun clearKeyBindingInputRules() {
        RuleRegistry.rules.remove(KeyBindingInputRule::class.java)
    }

    private class RecordingMouseButtonInputRule(private val receivedEvents: MutableList<MouseButtonEvent>) : MouseButtonInputRule {
        override fun onMouseButtonInput(event: MouseButtonEvent) {
            receivedEvents += event
        }
    }
}
