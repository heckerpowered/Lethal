/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.fundation.math.Geometry
import heckerpowered.fundation.math.RotatorView
import heckerpowered.fundation.math.VectorView
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minecraft convention:
 * - positive pitch looks downward, negative pitch looks upward.
 * - 0 faces +Z, -90 faces +X, and 90 faces -X.
 */
fun RotatorView.toViewVector(): VectorView {
    val pitchRadians = Math.toRadians(pitch)

    val yawRadians = Math.toRadians(-yaw)
    val yawCosine = cos(yawRadians)
    val yawSine = sin(yawRadians)

    val pitchCosine = cos(pitchRadians)
    val pitchSine = sin(pitchRadians)

    return Geometry.vector(yawSine * pitchCosine, -pitchSine, yawCosine * pitchCosine)
}