/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.math.sqrt

interface QuaternionView {
    val x: Double
    val y: Double
    val z: Double
    val w: Double

    val lengthSquared: Double
        get() = x * x + y * y + z * z + w * w

    val length: Double
        get() = sqrt(lengthSquared)
}