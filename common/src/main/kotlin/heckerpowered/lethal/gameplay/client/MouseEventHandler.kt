/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
import heckerpowered.bridge.adapter.item.Hand
import heckerpowered.bridge.adapter.item.asSlot
import heckerpowered.bridge.input.InputAction
import heckerpowered.bridge.input.MouseButton
import heckerpowered.bridge.input.MouseButtonEvent
import heckerpowered.bridge.input.MouseButtonInputRule
import heckerpowered.bridge.platform.Services
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.register
import heckerpowered.lethal.gameplay.common.item.firearm.Gun
import heckerpowered.lethal.gameplay.common.network.FireStatePayload

object MouseEventHandler : MouseButtonInputRule {
    private val FirearmTrigger = FirearmMouseInput(::hasEquippedGun) { isFiring ->
        Services.PayloadTransport.sendToServer(FireStatePayload(isFiring))
    }

    init {
        RuleRegistry.register<MouseButtonInputRule>(this)
    }

    fun onInitialize() {
    }

    override fun onMouseButtonInput(event: MouseButtonEvent) {
        FirearmTrigger.onMouseButtonInput(event)
    }

    private fun hasEquippedGun(): Boolean {
        val player = Services.ClientPlatform.minecraft.player ?: return false
        val equipment = player as? EntityEquipmentAccess ?: return false
        return Hand.entries.any { hand ->
            val item = equipment.getEquippedStack(hand.asSlot()).item
            item is Gun || item.form is Gun
        }
    }
}

internal class FirearmMouseInput(private val hasEquippedGun: () -> Boolean, private val sendFiringState: (Boolean) -> Unit) : MouseButtonInputRule {
    private var ownsCurrentLeftButtonPress = false

    override fun onMouseButtonInput(event: MouseButtonEvent) {
        if (event.button != MouseButton.Left) return

        when (event.action) {
            InputAction.Press -> handlePress(event)
            InputAction.Release -> handleRelease(event)
        }
    }

    private fun handlePress(event: MouseButtonEvent) {
        stopCapturedFiring()
        if (event.isCanceled || !hasEquippedGun()) return

        ownsCurrentLeftButtonPress = true
        event.cancel()
        sendFiringState(true)
    }

    private fun handleRelease(event: MouseButtonEvent) {
        if (!ownsCurrentLeftButtonPress) return

        ownsCurrentLeftButtonPress = false
        event.cancel()
        sendFiringState(false)
    }

    private fun stopCapturedFiring() {
        if (!ownsCurrentLeftButtonPress) return

        ownsCurrentLeftButtonPress = false
        sendFiringState(false)
    }
}
