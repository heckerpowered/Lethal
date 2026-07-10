/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.time

import kotlin.math.floor
import kotlin.time.Duration

class FixedRateRepeater(
    var frequency: Frequency,
    initialCredit: Double = 1.0,
    private val inactiveCreditLimit: Double = 1.0,
) {
    var paused: Boolean = false

    private var credit: Double = initialCredit
        .coerceAtLeast(0.0)
        .coerceAtMost(inactiveCreditLimit)

    val availableCredit: Double
        get() = credit

    fun updateInactive(deltaTime: Duration) {
        require(deltaTime >= Duration.ZERO)
        if (paused) return

        credit += deltaTime.inWholeNanoseconds.toDouble() / frequency.intervalNanos.toDouble()
        credit = credit.coerceAtMost(inactiveCreditLimit)
    }

    fun updateActive(deltaTime: Duration, maxOperations: Long = Long.MAX_VALUE): Long {
        require(deltaTime >= Duration.ZERO)
        require(maxOperations >= 0)

        if (paused) return 0L

        credit += deltaTime.inWholeNanoseconds.toDouble() / frequency.intervalNanos.toDouble()
        val operations = floor(credit).toLong().coerceAtMost(maxOperations)
        credit -= operations.toDouble()

        return operations
    }

    fun clear() {
        credit = 0.0
    }

    fun makeReady() {
        credit = credit
            .coerceAtLeast(1.0)
            .coerceAtMost(inactiveCreditLimit)
    }
}
