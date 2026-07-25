/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client

import heckerpowered.bridge.input.InputAction
import heckerpowered.bridge.input.MouseButton
import heckerpowered.bridge.input.MouseButtonEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirearmMouseInputTest {
    @Test
    fun rightButtonRemainsAvailableToNativeInteractions() {
        val firingStates = mutableListOf<Boolean>()
        val input = FirearmMouseInput({ true }, firingStates::add)
        val press = MouseButtonEvent(MouseButton.Right, InputAction.Press)
        val release = MouseButtonEvent(MouseButton.Right, InputAction.Release)

        input.onMouseButtonInput(press)
        input.onMouseButtonInput(release)

        assertFalse(press.isCanceled)
        assertFalse(release.isCanceled)
        assertEquals(emptyList(), firingStates)
    }

    @Test
    fun releaseRemainsNativeWhenGunIsEquippedAfterPress() {
        var hasEquippedGun = false
        val firingStates = mutableListOf<Boolean>()
        val input = FirearmMouseInput({ hasEquippedGun }, firingStates::add)
        val press = MouseButtonEvent(MouseButton.Left, InputAction.Press)

        input.onMouseButtonInput(press)
        hasEquippedGun = true
        val release = MouseButtonEvent(MouseButton.Left, InputAction.Release)
        input.onMouseButtonInput(release)

        assertFalse(press.isCanceled)
        assertFalse(release.isCanceled)
        assertEquals(emptyList(), firingStates)
    }

    @Test
    fun capturedReleaseStopsFiringAfterGunIsUnequipped() {
        var hasEquippedGun = true
        val firingStates = mutableListOf<Boolean>()
        val input = FirearmMouseInput({ hasEquippedGun }, firingStates::add)
        val press = MouseButtonEvent(MouseButton.Left, InputAction.Press)

        input.onMouseButtonInput(press)
        hasEquippedGun = false
        val release = MouseButtonEvent(MouseButton.Left, InputAction.Release)
        input.onMouseButtonInput(release)

        assertTrue(press.isCanceled)
        assertTrue(release.isCanceled)
        assertEquals(listOf(true, false), firingStates)
    }

    @Test
    fun unmatchedReleaseRemainsNativeWhileGunIsEquipped() {
        val firingStates = mutableListOf<Boolean>()
        val input = FirearmMouseInput({ true }, firingStates::add)
        val release = MouseButtonEvent(MouseButton.Left, InputAction.Release)

        input.onMouseButtonInput(release)

        assertFalse(release.isCanceled)
        assertEquals(emptyList(), firingStates)
    }

    @Test
    fun repeatedPressClosesCapturedInputBeforeReevaluatingEquipment() {
        var hasEquippedGun = true
        val firingStates = mutableListOf<Boolean>()
        val input = FirearmMouseInput({ hasEquippedGun }, firingStates::add)
        val firstPress = MouseButtonEvent(MouseButton.Left, InputAction.Press)

        input.onMouseButtonInput(firstPress)
        hasEquippedGun = false
        val secondPress = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        input.onMouseButtonInput(secondPress)
        val release = MouseButtonEvent(MouseButton.Left, InputAction.Release)
        input.onMouseButtonInput(release)

        assertTrue(firstPress.isCanceled)
        assertFalse(secondPress.isCanceled)
        assertFalse(release.isCanceled)
        assertEquals(listOf(true, false), firingStates)
    }

    @Test
    fun canceledPressDoesNotStartFiring() {
        val firingStates = mutableListOf<Boolean>()
        val input = FirearmMouseInput({ true }, firingStates::add)
        val press = MouseButtonEvent(MouseButton.Left, InputAction.Press)
        press.cancel()

        input.onMouseButtonInput(press)
        val release = MouseButtonEvent(MouseButton.Left, InputAction.Release)
        input.onMouseButtonInput(release)

        assertTrue(press.isCanceled)
        assertFalse(release.isCanceled)
        assertEquals(emptyList(), firingStates)
    }
}
