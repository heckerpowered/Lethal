/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.common.item.firearm

import heckerpowered.bridge.adapter.entity.PlayerAccess
import heckerpowered.bridge.adapter.item.Hand
import heckerpowered.bridge.adapter.item.asSlot
import heckerpowered.bridge.adapter.item.stack.ItemStackAccess
import heckerpowered.bridge.rule.RuleRegistry
import heckerpowered.bridge.rule.ServerUpdateRule
import heckerpowered.bridge.rule.register
import heckerpowered.bridge.time.FixedRateRepeater
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object FireStateTracker : ServerUpdateRule {
    private val defaultTickDuration = 50.milliseconds

    private val firingPlayers: MutableSet<PlayerAccess> = Collections.newSetFromMap(WeakHashMap())
    private val weaponStates: MutableMap<ItemStackAccess, FixedRateRepeater> = IdentityHashMap()

    init {
        RuleRegistry.register<ServerUpdateRule>(this)
    }

    /**
     * Adapts the host's server update callback to the default Minecraft tick duration.
     */
    override fun onServerUpdate() {
        tick()
    }

    /**
     * Records player-level trigger intent without binding it to the weapon equipped at input time.
     * A held trigger can therefore continue driving a different gun after an equipment change.
     */
    fun setFiring(player: PlayerAccess, firing: Boolean) {
        if (firing) {
            firingPlayers += player
        } else {
            firingPlayers -= player
        }
    }

    /**
     * Reports the last recorded trigger intent, not whether a gun exists, may fire, or produced a shot.
     */
    fun isFiring(player: PlayerAccess): Boolean {
        return player in firingPlayers
    }

    /**
     * Advances authoritative firing state by one logical game-time slice.
     * The trigger snapshot is resolved before scheduling so every tracked weapon state advances exactly once.
     */
    fun tick(deltaTime: Duration = defaultTickDuration) {
        require(deltaTime >= Duration.ZERO)

        val triggeredWeapons = collectTriggeredWeapons()
        prepareTriggeredWeaponStates(triggeredWeapons)
        updateWeaponStates(triggeredWeapons, deltaTime)
    }

    /**
     * Discards runtime-only trigger and cadence state at a lifecycle boundary.
     * Persistent weapon state, including durable cooldown rules, belongs to the weapon implementation.
     */
    internal fun clear() {
        firingPlayers.clear()
        weaponStates.clear()
    }

    /**
     * Captures the guns currently reached by held triggers.
     * Resolving equipment for every update lets input follow weapon switches instead of creating weapon-bound sessions.
     */
    private fun collectTriggeredWeapons(): IdentityHashMap<ItemStackAccess, TriggeredWeapon> {
        val triggeredWeapons = IdentityHashMap<ItemStackAccess, TriggeredWeapon>()

        for (player in firingPlayers.toList()) {
            addTriggeredWeapons(player, triggeredWeapons)
        }

        return triggeredWeapons
    }

    /**
     * Resolves both hands while preserving stack identity so distinct weapon instances keep independent cadence.
     */
    private fun addTriggeredWeapons(player: PlayerAccess, triggeredWeapons: IdentityHashMap<ItemStackAccess, TriggeredWeapon>) {
        for (hand in Hand.entries) {
            val weaponStack = player.getEquippedStack(hand.asSlot())
            val item = weaponStack.item
            val gun = item as? Gun ?: item.form as? Gun ?: continue
            triggeredWeapons[weaponStack] = TriggeredWeapon(player, gun)
        }
    }

    /**
     * Makes every triggered stack part of the upcoming single update pass.
     * Applying the latest frequency to an existing repeater preserves accumulated credit across dynamic rate changes.
     */
    private fun prepareTriggeredWeaponStates(triggeredWeapons: IdentityHashMap<ItemStackAccess, TriggeredWeapon>) {
        for ((weaponStack, triggeredWeapon) in triggeredWeapons) {
            val frequency = triggeredWeapon.gun.getFrequency(triggeredWeapon.player, weaponStack)
            val weaponState = weaponStates.getOrPut(weaponStack) {
                FixedRateRepeater(frequency)
            }
            weaponState.frequency = frequency
        }
    }

    /**
     * Owns temporal advancement for all weapon states, keeping triggered and untriggered updates mutually exclusive.
     * Fully recovered untriggered states are removed because recreating them as ready is behaviorally equivalent.
     */
    private fun updateWeaponStates(triggeredWeapons: IdentityHashMap<ItemStackAccess, TriggeredWeapon>, deltaTime: Duration) {
        val weaponStateIterator = weaponStates.entries.iterator()

        while (weaponStateIterator.hasNext()) {
            val (weaponStack, weaponState) = weaponStateIterator.next()
            val triggeredWeapon = triggeredWeapons[weaponStack]
            val shouldRemoveWeaponState = updateWeaponState(triggeredWeapon, weaponStack, weaponState, deltaTime)

            if (shouldRemoveWeaponState) {
                weaponStateIterator.remove()
            }
        }
    }

    /**
     * Selects the only valid progression mode for a weapon during this update.
     * The return value indicates that the state no longer carries unique cadence information and may be discarded.
     */
    private fun updateWeaponState(triggeredWeapon: TriggeredWeapon?, weaponStack: ItemStackAccess, weaponState: FixedRateRepeater, deltaTime: Duration): Boolean {
        if (triggeredWeapon == null) return updateUntriggeredWeaponState(weaponState, deltaTime)

        updateTriggeredWeapon(triggeredWeapon, weaponStack, weaponState, deltaTime)
        return false
    }

    /**
     * Recovers at most one ready operation while no trigger drives the weapon.
     * Once ready, retaining the runtime state would only keep the stack alive without changing future behavior.
     */
    private fun updateUntriggeredWeaponState(weaponState: FixedRateRepeater, deltaTime: Duration): Boolean {
        weaponState.updateInactive(deltaTime)
        return weaponState.availableCredit >= 1.0
    }

    /**
     * Converts accumulated cadence into one batched gun invocation after a single scheduling eligibility check.
     */
    private fun updateTriggeredWeapon(triggeredWeapon: TriggeredWeapon, weaponStack: ItemStackAccess, weaponState: FixedRateRepeater, deltaTime: Duration) {
        val player = triggeredWeapon.player
        val gun = triggeredWeapon.gun

        if (!gun.mayFire(player, weaponStack)) {
            weaponState.updateInactive(deltaTime)
            return
        }

        val operationCount = weaponState.updateActive(deltaTime)
        if (operationCount == 0L) return

        gun.fire(player, weaponStack, operationCount)
    }

    private data class TriggeredWeapon(val player: PlayerAccess, val gun: Gun)
}
