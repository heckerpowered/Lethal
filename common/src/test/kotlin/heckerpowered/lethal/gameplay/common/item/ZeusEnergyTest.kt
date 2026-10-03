/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item

import heckerpowered.bridge.adapter.item.ItemAccess
import heckerpowered.bridge.adapter.item.stack.PersistentDataAccess
import heckerpowered.bridge.adapter.item.stack.PersistentValueInput
import heckerpowered.bridge.adapter.item.stack.PersistentValueOutput
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.Constants
import heckerpowered.lethal.gameplay.common.item.firearm.WeaponEnergy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZeusEnergyTest {
    private val EnergyKey = Constants.identifier("zeus_energy")

    @Test
    fun startsEmptyWithoutCreatingStorageAndKeepsStacksIndependent() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val first = EnergyTestStack()
        val second = EnergyTestStack()

        assertEquals(0.0, energy.currentEnergyPoints(first))
        assertTrue(first.values.isEmpty())
        energy.addActualDamage(first, 35.0)

        assertEquals(35.0, energy.currentEnergyPoints(first))
        assertEquals(0.0, energy.currentEnergyPoints(second))
        assertTrue(second.values.isEmpty())
    }

    @Test
    fun persistsAcrossControllersAndSharesMissileAndJudgementBalance() {
        val stack = EnergyTestStack()
        WeaponEnergy(EnergyKey, 1_200.0).addActualDamage(stack, 600.0)
        val reloadedEnergy = WeaponEnergy(EnergyKey, 1_200.0)

        assertTrue(reloadedEnergy.tryConsumeEnergyPoints(stack, Zeus.MISSILE_COST_POINTS))
        assertEquals(500.0, reloadedEnergy.currentEnergyPoints(stack))
        assertTrue(reloadedEnergy.tryConsumeEnergyPoints(stack, Zeus.JUDGEMENT_COST_POINTS))
        assertEquals(0.0, reloadedEnergy.currentEnergyPoints(stack))
        assertEquals(1, stack.values.size)
    }

    @Test
    fun capsAccumulationAndRejectsInvalidDamageWithoutCreatingStorage() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        for (damage in listOf(0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            energy.addActualDamage(stack, damage)
        }
        assertTrue(stack.values.isEmpty())

        energy.addActualDamage(stack, 1_199.0)
        energy.addActualDamage(stack, Double.MAX_VALUE)
        assertEquals(1_200.0, energy.currentEnergyPoints(stack))
        energy.addActualDamage(stack, Double.MAX_VALUE)
        assertEquals(1_200.0, energy.currentEnergyPoints(stack))
    }

    @Test
    fun boundsMalformedStoredValuesWithoutMutatingReads() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val key = Constants.identifier("zeus_energy")
        val stack = EnergyTestStack()
        for ((stored, expected) in listOf(-1.0 to 0.0, Double.NaN to 0.0, Double.POSITIVE_INFINITY to 0.0, 4_800.0 to 1_200.0)) {
            stack.output.putDouble(key, stored)
            assertEquals(expected, energy.currentEnergyPoints(stack))
            assertEquals(stored, stack.input.getDouble(key))
        }
    }

    @Test
    fun insufficientEnergyDoesNotExecuteOrWrite() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        assertFalse(executePreparedPlan(energy, stack, 100.0) { error("Must not execute") })
        assertTrue(stack.values.isEmpty())
    }

    @Test
    fun platformCancellationAfterPaymentStillCountsAsSuccessfulExecution() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        energy.addActualDamage(stack, 600.0)
        var spawnAttempts = 0
        val platformSpawn = { spawnAttempts++; false }

        assertTrue(executePreparedPlan(energy, stack, 500.0) {
            assertEquals(100.0, energy.currentEnergyPoints(stack))
            assertFalse(platformSpawn())
        })
        assertEquals(1, spawnAttempts)
        assertEquals(100.0, energy.currentEnergyPoints(stack))
    }

    @Test
    fun executionExceptionsDoNotUndoCommittedPayment() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        energy.addActualDamage(stack, 600.0)
        assertFailsWith<IllegalStateException> {
            executePreparedPlan(energy, stack, 500.0) { error("Native execution failed") }
        }
        assertEquals(100.0, energy.currentEnergyPoints(stack))
        assertFailsWith<AssertionError> {
            executePreparedPlan(energy, stack, 100.0) { throw AssertionError("Fatal execution failure") }
        }
        assertEquals(0.0, energy.currentEnergyPoints(stack))
    }

    @Test
    fun nestedCastAcrossControllersSeesCommittedBalance() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        energy.addActualDamage(stack, 600.0)
        assertTrue(executePreparedPlan(energy, stack, 500.0) {
            val nestedEnergy = WeaponEnergy(EnergyKey, 1_200.0)
            assertEquals(100.0, nestedEnergy.currentEnergyPoints(stack))
            assertFalse(executePreparedPlan(nestedEnergy, stack, 500.0) { error("Insufficient nested balance") })
            assertTrue(executePreparedPlan(nestedEnergy, stack, 100.0) {})
        })
        assertEquals(0.0, energy.currentEnergyPoints(stack))
    }

    @Test
    fun committedPaymentMakesCapacityAvailableForOrdinaryShotRecharge() {
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        energy.addActualDamage(stack, 1_200.0)
        assertTrue(energy.tryConsumeEnergyPoints(stack, 500.0))
        assertEquals(700.0, energy.currentEnergyPoints(stack))
        energy.addActualDamage(stack, 500.0)
        assertEquals(1_200.0, energy.currentEnergyPoints(stack))
    }

    // Exercises caller ordering only; no C/V skill or native entity spawning is implemented here.
    private fun executePreparedPlan(energy: WeaponEnergy, stack: EnergyTestStack, cost: Double, execute: () -> Unit): Boolean {
        if (!energy.tryConsumeEnergyPoints(stack, cost)) return false
        execute()
        return true
    }

    @Test
    fun commonKeyPreservesBalanceWhenCapacityChangesWithoutClaimingAnUpgradeRoute() {
        val stack = EnergyTestStack()
        WeaponEnergy(EnergyKey, 1_200.0).addActualDamage(stack, 800.0)
        val largerCapacity = WeaponEnergy(EnergyKey, 2_400.0)
        assertEquals(800.0, largerCapacity.currentEnergyPoints(stack))
        largerCapacity.addActualDamage(stack, 800.0)
        assertEquals(1_600.0, largerCapacity.currentEnergyPoints(stack))
        assertEquals(1_200.0, WeaponEnergy(EnergyKey, 1_200.0).currentEnergyPoints(stack))
    }

    @Test
    fun rejectsInvalidCapacityAndCostsBeforeLaunching() {
        for (capacity in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { WeaponEnergy(EnergyKey, capacity) }
        }
        val energy = WeaponEnergy(EnergyKey, 1_200.0)
        val stack = EnergyTestStack()
        energy.addActualDamage(stack, 600.0)
        for (cost in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { energy.tryConsumeEnergyPoints(stack, cost) }
        }
        assertEquals(600.0, energy.currentEnergyPoints(stack))
    }

    @Test
    fun configuredKeysKeepDifferentWeaponsIndependentOnTheSameStack() {
        val stack = EnergyTestStack()
        val first = WeaponEnergy(Constants.identifier("first_weapon_energy"), 100.0)
        val second = WeaponEnergy(Constants.identifier("second_weapon_energy"), 200.0)
        first.addActualDamage(stack, 150.0)
        second.addActualDamage(stack, 150.0)

        assertEquals(100.0, first.currentEnergyPoints(stack))
        assertEquals(150.0, second.currentEnergyPoints(stack))
        assertTrue(first.tryConsumeEnergyPoints(stack, 50.0))
        assertEquals(50.0, first.currentEnergyPoints(stack))
        assertEquals(150.0, second.currentEnergyPoints(stack))
    }

    @Test
    fun usesApprovedCapacities() {
        assertEquals(1_200.0, Zeus.energy.maximumEnergyPoints)
        assertEquals(1_200.0, ZeusGolden.energy.maximumEnergyPoints)
        assertEquals(2_400.0, ZeusBlackGold.energy.maximumEnergyPoints)
        assertEquals(2_400.0, ZeusSculk.energy.maximumEnergyPoints)
    }
}

internal class EnergyTestStack : PersistentDataAccess {
    val values = mutableMapOf<String, Double>()
    override val item: ItemAccess get() = error("Energy does not require item access")
    override var count = 1
    override var damagePoints = 0
    override val maxStackCount = 1
    override val maxDamagePoints = 0
    override val input = object : PersistentValueInput {
        override fun getLong(key: Identifier): Long? = null
        override fun getDouble(key: Identifier): Double? = values[key.asString()]
    }
    override val output = object : PersistentValueOutput {
        override fun putLong(key: Identifier, value: Long): Unit = error("Energy uses double storage")
        override fun putDouble(key: Identifier, value: Double) { values[key.asString()] = value }
        override fun remove(key: Identifier) { values.remove(key.asString()) }
    }
}
