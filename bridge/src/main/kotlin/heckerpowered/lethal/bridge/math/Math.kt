/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.math

import kotlin.math.abs

fun Double.square() = this * this

fun Double.isNearlyZero(epsilon: Double = 1.0E-8): Boolean {
    return abs(this) <= epsilon
}