/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FixedRateRepeaterTest {
    @Test
    fun rejectsNegativeInactiveCreditLimit() {
        assertFailsWith<IllegalArgumentException> { FixedRateRepeater(Frequency.perSecond(1), 1.0, -1.0) }
    }

    @Test
    fun emitsImmediatelyAndAccumulatesElapsedTime() {
        val repeater = FixedRateRepeater(Frequency.perMinute(600))

        val operations = List(4) {
            repeater.updateActive(50.milliseconds)
        }

        assertEquals(expected = listOf(1L, 1L, 0L, 1L), actual = operations)
    }

    @Test
    fun emitsMultipleOperationsWithinOneWindow() {
        val repeater = FixedRateRepeater(Frequency.perMinute(2400))

        val operations = List(3) {
            repeater.updateActive(50.milliseconds)
        }

        assertEquals(expected = listOf(3L, 2L, 2L), actual = operations)
    }

    @Test
    fun changingFrequencyOnlyAffectsFutureProgress() {
        val repeater = FixedRateRepeater(Frequency.perMinute(600))

        assertEquals(expected = 1L, actual = repeater.updateActive(Duration.ZERO))
        repeater.updateInactive(50.milliseconds)
        repeater.frequency = Frequency.perMinute(1200)

        assertEquals(expected = 0.5, actual = repeater.availableCredit)
        assertEquals(expected = 1L, actual = repeater.updateActive(25.milliseconds))
        assertEquals(expected = 0L, actual = repeater.updateActive(Duration.ZERO))
    }

    @Test
    fun inactiveUpdatesDoNotAccumulateBacklog() {
        val repeater = FixedRateRepeater(Frequency.perSecond(1000), 0.0)

        repeater.updateInactive(10.seconds)

        assertEquals(expected = 1.0, actual = repeater.availableCredit)
        assertEquals(expected = 1L, actual = repeater.updateActive(Duration.ZERO))
    }

    @Test
    fun operationLimitLeavesUnconsumedCredit() {
        val repeater = FixedRateRepeater(Frequency.perSecond(1000))

        assertEquals(expected = 8L, actual = repeater.updateActive(50.milliseconds, 8))
        assertEquals(expected = 8L, actual = repeater.updateActive(Duration.ZERO, 8))
        assertEquals(expected = 8L, actual = repeater.updateActive(Duration.ZERO, 8))
    }
}
