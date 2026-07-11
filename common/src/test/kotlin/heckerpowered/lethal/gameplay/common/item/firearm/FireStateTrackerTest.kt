/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.EquipmentSlot
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.ItemForm
import heckerpowered.bridge.adapter.item.ItemProperties
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.ServerUpdateRule
import heckerpowered.bridge.rule.forEach
import heckerpowered.bridge.time.Frequency
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FireStateTrackerTest {
    private val emptyStack = TestItemStack(
        item = TestRegularItem,
        count = 0,
    )

    @BeforeTest
    fun setUp() {
        FireStateTracker.clear()
    }

    @AfterTest
    fun tearDown() {
        FireStateTracker.clear()
    }

    @Test
    fun heldTriggerFiresImmediatelyAndThenFollowsFrequency() {
        val gun = TestGun(Frequency.perMinute(600))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer(mainHandStack = { weaponStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()
        assertEquals(1, gun.fireCount)

        FireStateTracker.tick()
        assertEquals(2, gun.fireCount)

        FireStateTracker.tick()
        assertEquals(2, gun.fireCount)
    }

    @Test
    fun registeredServerUpdateAdvancesFiring() {
        val gun = TestGun(Frequency.perMinute(600))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer(mainHandStack = { weaponStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)

        RuleRegistry.forEach<ServerUpdateRule> { it.onServerUpdate() }

        assertEquals(1, gun.fireCount)
    }

    @Test
    fun pressingAgainDoesNotResetFireProgress() {
        val gun = TestGun(Frequency.perMinute(600))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer(mainHandStack = { weaponStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)
        FireStateTracker.tick()

        FireStateTracker.setFiring(player, false)
        FireStateTracker.setFiring(player, true)
        FireStateTracker.tick(25.milliseconds)

        assertEquals(1, gun.fireCount)

        FireStateTracker.tick(25.milliseconds)
        assertEquals(2, gun.fireCount)
    }

    @Test
    fun heldTriggerUsesTheCurrentlyEquippedGun() {
        val firstGun = TestGun(Frequency.perMinute(600))
        val secondGun = TestGun(Frequency.perMinute(600))
        var mainHandStack = TestItemStack(firstGun)
        val player = createPlayer(mainHandStack = { mainHandStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()
        mainHandStack = TestItemStack(secondGun)
        FireStateTracker.tick()

        assertEquals(1, firstGun.fireCount)
        assertEquals(1, secondGun.fireCount)
        assertTrue(FireStateTracker.isFiring(player))
    }

    @Test
    fun blockedGunDoesNotAccumulateOperations() {
        val gun = TestGun(Frequency.perMinute(6000), firingAllowed = false)
        val weaponStack = TestItemStack(gun)
        val player = createPlayer(mainHandStack = { weaponStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)

        repeat(20) {
            FireStateTracker.tick()
        }
        gun.firingAllowed = true
        FireStateTracker.tick()

        assertEquals(6, gun.fireCount)
    }

    @Test
    fun highFrequencyIsNotLimitedByTheTracker() {
        val gun = TestGun(Frequency.perMinute(24000))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer(mainHandStack = { weaponStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()

        assertEquals(21, gun.fireCount)
    }

    @Test
    fun batchedOperationsStopWhenFireFails() {
        val gun = TestGun(Frequency.perMinute(6000))
        gun.maximumSuccessfulFireCount = 1
        val weaponStack = TestItemStack(gun)
        val player = createPlayer(mainHandStack = { weaponStack }, offHandStack = { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()

        assertEquals(1, gun.fireCount)
    }

    @Test
    fun bothEquippedGunsReceiveTheHeldTrigger() {
        val mainHandGun = TestGun(Frequency.perMinute(600))
        val offHandGun = TestGun(Frequency.perMinute(600))
        val mainHandStack = TestItemStack(mainHandGun)
        val offHandStack = TestItemStack(offHandGun)
        val player = createPlayer(
            mainHandStack = { mainHandStack },
            offHandStack = { offHandStack },
        )
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()

        assertEquals(1, mainHandGun.fireCount)
        assertEquals(1, offHandGun.fireCount)
    }

    private fun createPlayer(mainHandStack: () -> ItemStackAccess, offHandStack: () -> ItemStackAccess): PlayerAccess {
        return Proxy.newProxyInstance(
            PlayerAccess::class.java.classLoader,
            arrayOf(PlayerAccess::class.java),
        ) { player, method, arguments ->
            when (method.name) {
                "getEquippedStack" -> when (arguments?.firstOrNull() as EquipmentSlot) {
                    EquipmentSlot.MainHand -> mainHandStack()
                    EquipmentSlot.OffHand -> offHandStack()
                    else -> error("Unsupported equipment slot")
                }

                "hashCode" -> System.identityHashCode(player)
                "equals" -> player === arguments?.firstOrNull()
                "toString" -> "TestPlayerAccess"
                else -> error("Unsupported PlayerAccess method: ${method.name}")
            }
        } as PlayerAccess
    }

    private class TestGun(
        private val frequency: Frequency,
        var firingAllowed: Boolean = true,
    ) : Firearm() {
        override val identifier: Identifier
            get() = error("Identifier is not used by this test")
        override val properties: ItemProperties = ItemProperties()
        var fireCount: Int = 0
        var maximumSuccessfulFireCount: Int = Int.MAX_VALUE

        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
            return frequency
        }

        override fun mayFire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
            return firingAllowed
        }

        override fun fire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
            if (!firingAllowed || fireCount >= maximumSuccessfulFireCount) return false

            fireCount++
            return true
        }

        override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess) {
        }
    }

    private class TestItemStack(
        override val item: ItemAccess,
        override var count: Int = 1,
        override var damagePoints: Int = 0,
        override val maxStackCount: Int = 1,
        override val maxDamagePoints: Int = 0,
    ) : ItemStackAccess

    private object TestRegularItem : ItemAccess {
        override val identifier: Identifier
            get() = error("Identifier is not used by this test")
        override val form: ItemForm = ItemForm.Regular
    }
}
