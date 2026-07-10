/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class Frequency(
    val duration: Duration,
    val times: Long,
) {
    companion object {
        fun per(unit: DurationUnit, times: Long) = Frequency(1.toDuration(unit), times)
        fun perSecond(times: Long) = per(DurationUnit.SECONDS, times)
        fun perMinute(times: Long) = per(DurationUnit.MINUTES, times)
    }

    init {
        require(duration > Duration.ZERO)
        require(times > 0)
        require(duration.inWholeNanoseconds / times > 0)
    }

    val interval: Duration
        get() = (duration.toLong(DurationUnit.NANOSECONDS) / times).nanoseconds
    val intervalNanos: Long = duration.inWholeNanoseconds / times

    fun operationsIn(elapsedTime: Duration): Double {
        require(elapsedTime >= Duration.ZERO)
        return elapsedTime.inWholeNanoseconds.toDouble() * times.toDouble() / duration.inWholeNanoseconds.toDouble()
    }
}
