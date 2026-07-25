/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.input

import heckerpowered.bridge.input.KeyBindingInputRule
import heckerpowered.bridge.input.KeyBindingPressedEvent
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.lethal.gameplay.common.network.SkillActivationPayload
import heckerpowered.lethal.gameplay.common.skill.SkillSlot

object SkillKeyInputHandler : KeyBindingInputRule {
    private val input = SkillKeyInput(
        hasPlayer = { Services.ClientPlatform.minecraft.player != null },
        sendActivation = { slot ->
            Services.PayloadTransport.sendToServer(SkillActivationPayload(slot))
        },
    )

    init {
        RuleRegistry.register<KeyBindingInputRule>(this)
    }

    fun onInitialize() {
    }

    override fun onKeyBindingInput(event: KeyBindingPressedEvent) {
        input.onKeyBindingInput(event)
    }
}

internal class SkillKeyInput(
    private val hasPlayer: () -> Boolean,
    private val sendActivation: (SkillSlot) -> Unit,
) : KeyBindingInputRule {
    override fun onKeyBindingInput(event: KeyBindingPressedEvent) {
        val slot = skillSlotsByBinding[event.bindingIdentifier.asString()] ?: return
        if (!hasPlayer()) return

        sendActivation(slot)
    }

    private companion object {
        val skillSlotsByBinding = mapOf(
            ModKeyBindings.PrimarySkill.identifier.asString() to SkillSlot.Primary,
            ModKeyBindings.SecondarySkill.identifier.asString() to SkillSlot.Secondary,
            ModKeyBindings.UltimateSkill.identifier.asString() to SkillSlot.Ultimate,
        )
    }
}
