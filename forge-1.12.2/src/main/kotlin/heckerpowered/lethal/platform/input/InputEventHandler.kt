/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.input

import heckerpowered.bridge.input.ClientInput
import heckerpowered.bridge.input.InputAction
import heckerpowered.bridge.input.MouseButton
import heckerpowered.bridge.input.MouseButtonEvent
import heckerpowered.lethal.Constants
import net.minecraftforge.client.event.MouseEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
class InputEventHandler private constructor() {
    companion object {
        @SubscribeEvent
        @JvmStatic
        fun onMouseInput(event: MouseEvent) {
            val mouseButton = translateMouseButton(event.button) ?: return
            val inputAction = if (event.isButtonstate) InputAction.Press else InputAction.Release
            ClientInput.handle(MouseButtonEvent(mouseButton, inputAction))
        }

        private fun translateMouseButton(buttonCode: Int): MouseButton? {
            return when (buttonCode) {
                0 -> MouseButton.Left
                1 -> MouseButton.Right
                else -> null
            }
        }
    }
}
