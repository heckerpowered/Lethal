/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.skill.zeus

import heckerpowered.lethal.gameplay.common.skill.SkillSlot
import heckerpowered.lethal.gameplay.common.item.firearm.WeaponEnergy
import heckerpowered.lethal.Constants
import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
import heckerpowered.bridge.adapter.item.stack.PersistentValueInput
import heckerpowered.bridge.adapter.item.stack.PersistentValueOutput
import heckerpowered.bridge.input.KeyBindingPressedEvent
import heckerpowered.bridge.input.KeyboardKey
import heckerpowered.bridge.resources.Identifier
import heckerpowered.bridge.time.FixedRateRepeater
import heckerpowered.bridge.time.Frequency
import heckerpowered.lethal.gameplay.client.input.ModKeyBindings
import heckerpowered.lethal.gameplay.client.input.SkillKeyInput
import heckerpowered.lethal.gameplay.common.item.*
import java.lang.reflect.Proxy
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class ZeusBerserkSkillTest {
    private val player = Proxy.newProxyInstance(PlayerAccess::class.java.classLoader, arrayOf(PlayerAccess::class.java)) { _, method, _ ->
        error("Berserk must not invoke player effects: ${method.name}")
    } as PlayerAccess

    @Test
    fun activityAndCooldownEndAtTheirExactBoundariesWithoutReadWrites() {
        var now = 1_000L
        val skill = ZeusBerserkSkill { now }
        val stack = BerserkStack()
        assertEquals(ZeusBerserkStatus.Inactive, skill.status(stack))
        assertEquals(1L, skill.frequencyMultiplier(stack))
        assertEquals(0, stack.writes)
        skill.activate(player, stack)
        assertEquals(1, stack.writes)
        now = 10_999L
        assertEquals(2L, skill.frequencyMultiplier(stack))
        assertEquals(1L, skill.status(stack).activeRemainingMilliseconds)
        now = 11_000L
        assertEquals(1L, skill.frequencyMultiplier(stack))
        assertEquals(30_000L, skill.status(stack).cooldownRemainingMilliseconds)
        now = 40_999L
        skill.activate(player, stack)
        assertEquals(1, stack.writes)
        now = 41_000L
        skill.activate(player, stack)
        assertEquals(2, stack.writes)
        assertTrue(skill.status(stack).isActive)
    }

    @Test
    fun activationIsFreeIndependentAndSurvivesControllerReload() {
        val first = BerserkStack()
        val second = BerserkStack()
        val energy = WeaponEnergy(Constants.identifier("zeus_energy"), 1_200.0)
        energy.addActualDamage(first, 600.0)
        val skill = ZeusBerserkSkill { 5_000L }
        skill.activate(player, first)
        assertEquals(600.0, energy.currentEnergyPoints(first))
        assertEquals(0, second.writes)
        assertFalse(skill.status(second).isActive)
        assertTrue(ZeusBerserkSkill { 10_000L }.status(first).isActive)
        val reloaded = ZeusBerserkSkill { 45_000L }
        assertEquals(ZeusBerserkStatus.Inactive, reloaded.status(first))
        reloaded.activate(player, first)
        assertTrue(reloaded.status(first).isActive)
        assertEquals(600.0, energy.currentEnergyPoints(first))
    }

    @Test
    fun backwardAndNegativeClocksDoNotUnlockCooldownOrOverflow() {
        var now = 1_000L
        val skill = ZeusBerserkSkill { now }
        val stack = BerserkStack()
        now = -1L
        skill.activate(player, stack)
        assertEquals(0, stack.writes)
        now = 1_000L
        skill.activate(player, stack)
        for (time in listOf(999L, -1L, Long.MIN_VALUE)) {
            now = time
            assertFalse(skill.status(stack).isActive)
            assertEquals(40_000L, skill.status(stack).cooldownRemainingMilliseconds)
            skill.activate(player, stack)
            assertEquals(1, stack.writes)
        }
        now = Long.MAX_VALUE
        assertEquals(ZeusBerserkStatus.Inactive, skill.status(stack))
        skill.activate(player, stack)
        assertEquals(10_000L, skill.status(stack).activeRemainingMilliseconds)
    }

    @Test
    fun activationWithoutPersistenceDoesNothing() {
        val stack = Proxy.newProxyInstance(ItemStackAccess::class.java.classLoader, arrayOf(ItemStackAccess::class.java)) { _, method, _ ->
            error("Unsupported stack must not be accessed: ${method.name}")
        } as ItemStackAccess
        val skill = ZeusBerserkSkill { 1_000L }
        skill.activate(player, stack)
        assertEquals(1L, skill.frequencyMultiplier(stack))
    }

    @Test
    fun allExistingZeusVariantsUseBerserkAndFortuneWeaponsIgnoreIt() {
        val stack = BerserkStack()
        for (weapon in listOf(Zeus, ZeusGolden, ZeusBlackGold, ZeusSculk, ZeusGlowSquid)) {
            assertSame(ZeusBerserk, weapon.getSkill(SkillSlot.Auxiliary))
            assertNull(weapon.getSkill(SkillSlot.Primary))
            assertEquals(Frequency.perSecond(2), weapon.getFrequency(player, stack))
        }
        ZeusBerserk.activate(player, stack)
        for (weapon in listOf(Zeus, ZeusGolden, ZeusBlackGold, ZeusSculk, ZeusGlowSquid)) {
            assertEquals(Frequency.perSecond(4), weapon.getFrequency(player, stack))
        }
        assertNull(Fortune.getSkill(SkillSlot.Auxiliary))
        assertNull(EnhancedFortune.getSkill(SkillSlot.Auxiliary))
    }

    @Test
    fun newKeyRouteAndSlotKeepExistingNetworkIds() {
        val received = mutableListOf<SkillSlot>()
        val input = SkillKeyInput({ true }, received::add)
        assertEquals(KeyboardKey.Z, ModKeyBindings.AuxiliarySkill.defaultKey)
        input.onKeyBindingInput(KeyBindingPressedEvent(ModKeyBindings.AuxiliarySkill.identifier))
        assertEquals(listOf(SkillSlot.Auxiliary), received)
        SkillKeyInput({ false }, received::add).onKeyBindingInput(KeyBindingPressedEvent(ModKeyBindings.AuxiliarySkill.identifier))
        assertEquals(1, received.size)
        for ((id, slot) in listOf(SkillSlot.Primary, SkillSlot.Secondary, SkillSlot.Ultimate, SkillSlot.Auxiliary).withIndex()) {
            assertEquals(id.toByte(), slot.networkId)
            assertEquals(slot, SkillSlot.fromNetworkId(id.toByte()))
        }
        assertFailsWith<IllegalArgumentException> { SkillSlot.fromNetworkId(4) }
    }

    @Test
    fun berserkProvidesOnlyAnActiveFrequencyMultiplier() {
        var now = 1_000L
        val skill = ZeusBerserkSkill { now }
        val stack = BerserkStack()
        assertEquals(1L, skill.frequencyMultiplier(stack))

        skill.activate(player, stack)
        assertEquals(2L, skill.frequencyMultiplier(stack))
        now = 11_000L
        assertEquals(1L, skill.frequencyMultiplier(stack))
    }

    @Test
    fun dynamicCadenceRetainsCreditAcrossRateChanges() {
        val repeater = FixedRateRepeater(Frequency.perSecond(2), initialCredit = 0.0)
        assertEquals(0L, repeater.updateActive(100.milliseconds))
        repeater.frequency = Frequency.perSecond(4)
        assertEquals(0L, repeater.updateActive(150.milliseconds))
        assertEquals(1L, repeater.updateActive(50.milliseconds))
        repeater.frequency = Frequency.perSecond(2)
        assertEquals(0L, repeater.updateActive(250.milliseconds))
        assertEquals(1L, repeater.updateActive(250.milliseconds))
    }

    private class BerserkStack : PersistentDataAccess {
        private val timestamps = mutableMapOf<String, Long>()
        private val energy = mutableMapOf<String, Double>()
        var writes = 0
        override val item: ItemAccess get() = error("Berserk does not inspect the item")
        override var count = 1
        override var damagePoints = 0
        override val maxStackCount = 1
        override val maxDamagePoints = 0
        override val input = object : PersistentValueInput {
            override fun getLong(key: Identifier): Long? = timestamps[key.asString()]
            override fun getDouble(key: Identifier): Double? = energy[key.asString()]
        }
        override val output = object : PersistentValueOutput {
            override fun putLong(key: Identifier, value: Long) { timestamps[key.asString()] = value; writes++ }
            override fun putDouble(key: Identifier, value: Double) { energy[key.asString()] = value }
            override fun remove(key: Identifier) { timestamps.remove(key.asString()); energy.remove(key.asString()) }
        }
    }
}
