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
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.client.event.MouseEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import org.lwjgl.opengl.Display
import java.util.EnumSet

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = [Side.CLIENT])
object InputEventHandler {
    private val pressedMouseButtons: MutableSet<MouseButton> = EnumSet.noneOf(MouseButton::class.java)
    private var wasDisplayActive = true

    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: MouseEvent) {
        val mouseButton = translateMouseButton(event.button) ?: return
        val inputAction = if (event.isButtonstate) InputAction.Press else InputAction.Release
        val mouseButtonEvent = MouseButtonEvent(mouseButton, inputAction)
        recordMouseButtonState(mouseButton, inputAction)
        ClientInput.handle(mouseButtonEvent)

        if (!mouseButtonEvent.isCanceled) return
        event.isCanceled = true
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        ForgeKeyBindings.dispatchPressedBindings()
    }

    @SubscribeEvent
    @JvmStatic
    fun onGuiOpen(event: GuiOpenEvent) {
        if (event.gui == null) return
        releaseMouseButtons()
    }

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return

        val isDisplayActive = Display.isActive()
        if (wasDisplayActive && !isDisplayActive) {
            releaseMouseButtons()
        }
        wasDisplayActive = isDisplayActive
    }

    private fun releaseMouseButtons() {
        val mouseButtonsToRelease = pressedMouseButtons.toList()
        pressedMouseButtons.clear()
        for (mouseButton in mouseButtonsToRelease) {
            ClientInput.handle(MouseButtonEvent(mouseButton, InputAction.Release))
        }
    }

    private fun recordMouseButtonState(mouseButton: MouseButton, inputAction: InputAction) {
        when (inputAction) {
            InputAction.Press -> pressedMouseButtons += mouseButton
            InputAction.Release -> pressedMouseButtons -= mouseButton
        }
    }

    private fun translateMouseButton(buttonCode: Int): MouseButton? {
        return when (buttonCode) {
            0 -> MouseButton.Left
            1 -> MouseButton.Right
            else -> null
        }
    }
}
