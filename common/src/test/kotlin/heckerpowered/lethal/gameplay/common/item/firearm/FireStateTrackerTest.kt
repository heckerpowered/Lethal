/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.EntityEquipmentAccess
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
    private val emptyStack = TestItemStack(TestRegularItem, 0)

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
        val player = createPlayer({ weaponStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()
        assertEquals(expected = 1, actual = gun.fireCount)

        FireStateTracker.tick()
        assertEquals(expected = 2, actual = gun.fireCount)

        FireStateTracker.tick()
        assertEquals(expected = 2, actual = gun.fireCount)
    }

    @Test
    fun registeredServerUpdateAdvancesFiring() {
        val gun = TestGun(Frequency.perMinute(600))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer({ weaponStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)

        RuleRegistry.forEach<ServerUpdateRule> { it.onServerUpdate() }

        assertEquals(expected = 1, actual = gun.fireCount)
    }

    @Test
    fun pressingAgainDoesNotResetFireProgress() {
        val gun = TestGun(Frequency.perMinute(600))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer({ weaponStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)
        FireStateTracker.tick()

        FireStateTracker.setFiring(player, false)
        FireStateTracker.setFiring(player, true)
        FireStateTracker.tick(25.milliseconds)

        assertEquals(expected = 1, actual = gun.fireCount)

        FireStateTracker.tick(25.milliseconds)
        assertEquals(expected = 2, actual = gun.fireCount)
    }

    @Test
    fun heldTriggerUsesTheCurrentlyEquippedGun() {
        val firstGun = TestGun(Frequency.perMinute(600))
        val secondGun = TestGun(Frequency.perMinute(600))
        var mainHandStack = TestItemStack(firstGun)
        val player = createPlayer({ mainHandStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()
        mainHandStack = TestItemStack(secondGun)
        FireStateTracker.tick()

        assertEquals(expected = 1, actual = firstGun.fireCount)
        assertEquals(expected = 1, actual = secondGun.fireCount)
        assertTrue(FireStateTracker.isFiring(player))
    }

    @Test
    fun blockedGunDoesNotAccumulateOperations() {
        val gun = TestGun(Frequency.perMinute(6000), false)
        val weaponStack = TestItemStack(gun)
        val player = createPlayer({ weaponStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)

        repeat(20) {
            FireStateTracker.tick()
        }
        gun.firingAllowed = true
        FireStateTracker.tick()

        assertEquals(expected = 6, actual = gun.fireCount)
    }

    @Test
    fun highFrequencyIsNotLimitedByTheTracker() {
        val gun = TestGun(Frequency.perMinute(24000))
        val weaponStack = TestItemStack(gun)
        val player = createPlayer({ weaponStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()

        assertEquals(expected = 21, actual = gun.fireCount)
    }

    @Test
    fun batchedOperationsUseOneFireCallAndStopWhenFireFails() {
        val gun = TestGun(Frequency.perMinute(6000))
        gun.maximumSuccessfulFireCount = 1
        val weaponStack = TestItemStack(gun)
        val player = createPlayer({ weaponStack }, { emptyStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()

        assertEquals(expected = 1, actual = gun.fireCount)
        assertEquals(expected = 1, actual = gun.fireCallCount)
    }

    @Test
    fun bothEquippedGunsReceiveTheHeldTrigger() {
        val mainHandGun = TestGun(Frequency.perMinute(600))
        val offHandGun = TestGun(Frequency.perMinute(600))
        val mainHandStack = TestItemStack(mainHandGun)
        val offHandStack = TestItemStack(offHandGun)
        val player = createPlayer({ mainHandStack }, { offHandStack })
        FireStateTracker.setFiring(player, true)

        FireStateTracker.tick()

        assertEquals(expected = 1, actual = mainHandGun.fireCount)
        assertEquals(expected = 1, actual = offHandGun.fireCount)
    }

    private fun createPlayer(mainHandStack: () -> ItemStackAccess, offHandStack: () -> ItemStackAccess): PlayerAccess {
        return Proxy.newProxyInstance(
            PlayerAccess::class.java.classLoader,
            arrayOf(PlayerAccess::class.java, EntityEquipmentAccess::class.java),
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

    private class TestGun(private val frequency: Frequency, var firingAllowed: Boolean = true) : Firearm() {
        override val identifier: Identifier
            get() = error("Identifier is not used by this test")
        override val properties: ItemProperties = ItemProperties()
        var fireCount: Int = 0
        var fireCallCount: Int = 0
        var maximumSuccessfulFireCount: Int = Int.MAX_VALUE

        override fun getFrequency(player: PlayerAccess, weaponStack: ItemStackAccess): Frequency {
            return frequency
        }

        override fun mayFire(player: PlayerAccess, weaponStack: ItemStackAccess): Boolean {
            return firingAllowed
        }

        override fun fire(player: PlayerAccess, weaponStack: ItemStackAccess, requestedShotCount: Long): Long {
            require(requestedShotCount >= 0)
            fireCallCount++
            if (!firingAllowed) return 0

            val remainingSuccessfulFireCount = (maximumSuccessfulFireCount - fireCount).coerceAtLeast(0).toLong()
            val firedShotCount = minOf(requestedShotCount, remainingSuccessfulFireCount)
            fireCount += firedShotCount.toInt()
            return firedShotCount
        }

        override fun shoot(player: PlayerAccess, weaponStack: ItemStackAccess, shotCount: Long) {
        }
    }

    private class TestItemStack(override val item: ItemAccess, override var count: Int = 1, override var damagePoints: Int = 0, override val maxStackCount: Int = 1, override val maxDamagePoints: Int = 0) : ItemStackAccess

    private object TestRegularItem : ItemAccess {
        override val identifier: Identifier
            get() = error("Identifier is not used by this test")
        override val form: ItemForm = ItemForm.Regular
    }
}
