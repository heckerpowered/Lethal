/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.adapter.item.EquipmentSlot
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
    init {
        RuleRegistry.register<MouseButtonInputRule>(this)
    }

    fun onInitialize() {
    }

    override fun onMouseButtonInput(event: MouseButtonEvent) {
        if (event.button != MouseButton.Left) return
        val minecraft = Services.Platform.minecraft
        val player = minecraft.player ?: return
        if (player.getEquippedStack(EquipmentSlot.MainHand).item.form !is Gun &&
            player.getEquippedStack(EquipmentSlot.OffHand).item.form !is Gun
        ) {
            return
        }
        event.cancel()

        Services.PayloadTransport.sendToServer(FireStatePayload(event.action == InputAction.Press))
    }
}