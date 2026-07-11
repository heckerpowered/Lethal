/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FixedRateRepeaterTest {
    @Test
    fun emitsImmediatelyAndAccumulatesElapsedTime() {
        val repeater = FixedRateRepeater(Frequency.perMinute(600))

        val operations = List(4) {
            repeater.updateActive(50.milliseconds)
        }

        assertEquals(listOf(1L, 1L, 0L, 1L), operations)
    }

    @Test
    fun emitsMultipleOperationsWithinOneWindow() {
        val repeater = FixedRateRepeater(Frequency.perMinute(2400))

        val operations = List(3) {
            repeater.updateActive(50.milliseconds)
        }

        assertEquals(listOf(3L, 2L, 2L), operations)
    }

    @Test
    fun changingFrequencyOnlyAffectsFutureProgress() {
        val repeater = FixedRateRepeater(Frequency.perMinute(600))

        assertEquals(1L, repeater.updateActive(Duration.ZERO))
        repeater.updateInactive(50.milliseconds)
        repeater.frequency = Frequency.perMinute(1200)

        assertEquals(0.5, repeater.availableCredit)
        assertEquals(1L, repeater.updateActive(25.milliseconds))
        assertEquals(0L, repeater.updateActive(Duration.ZERO))
    }

    @Test
    fun inactiveUpdatesDoNotAccumulateBacklog() {
        val repeater = FixedRateRepeater(Frequency.perSecond(1000), initialCredit = 0.0)

        repeater.updateInactive(10.seconds)

        assertEquals(1.0, repeater.availableCredit)
        assertEquals(1L, repeater.updateActive(Duration.ZERO))
    }

    @Test
    fun operationLimitLeavesUnconsumedCredit() {
        val repeater = FixedRateRepeater(Frequency.perSecond(1000))

        assertEquals(8L, repeater.updateActive(50.milliseconds, maxOperations = 8))
        assertEquals(8L, repeater.updateActive(Duration.ZERO, maxOperations = 8))
        assertEquals(8L, repeater.updateActive(Duration.ZERO, maxOperations = 8))
    }
}
