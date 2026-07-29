/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.ContinuousUseItem
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class FirearmTest {
    private val player = proxy<PlayerAccess>()
    private val weaponStack = proxy<ItemStackAccess>()

    @Test
    fun firePassesRequestedShotCountToOneShootCall() {
        val firearm = RecordingFirearm()

        val firedShotCount = firearm.fire(player, weaponStack, 3)

        assertEquals(expected = 3, actual = firedShotCount)
        assertEquals(expected = 3, actual = firearm.receivedShotCount)
        assertEquals(expected = 1, actual = firearm.shootCallCount)
    }

    @Test
    fun fireDoesNotShootWhenTheWeaponMayNotFire() {
        val firearm = RecordingFirearm(false)

        val firedShotCount = firearm.fire(player, weaponStack, 5)

        assertEquals(expected = 0, actual = firedShotCount)
        assertEquals(expected = 0, actual = firearm.receivedShotCount)
        assertEquals(expected = 0, actual = firearm.shootCallCount)
    }

    @Test
    fun fireRejectsNegativeShotCount() {
        val firearm = RecordingFirearm()

        assertFailsWith<IllegalArgumentException> {
            firearm.fire(player, weaponStack, -1)
        }
    }

    @Test
    fun firearmDoesNotDeclareContinuousUseFeature() {
        val firearm: ItemAccess = RecordingFirearm()

        assertFalse(firearm is ContinuousUseItem)
    }

    private inline fun <reified Access : Any> proxy(): Access {
        return Proxy.newProxyInstance(Access::class.java.classLoader, arrayOf(Access::class.java)) { _, method, _ ->
            error("Unsupported " + Access::class.java.simpleName + " method: " + method.name)
        } as Access
    }

    private class RecordingFirearm(private val firingAllowed: Boolean = true) : Firearm() {
        override val identifier: Identifier
            get() = error("Identifier is not used by this test")

        var receivedShotCount = 0L
            private set
        var shootCallCount = 0
            private set

        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess) = error("Frequency is not used by this test")

        override fun mayFire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
            return firingAllowed
        }

        override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
            receivedShotCount = shotCount
            shootCallCount++
        }
    }
}
