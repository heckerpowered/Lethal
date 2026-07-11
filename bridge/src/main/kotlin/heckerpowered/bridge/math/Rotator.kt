/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import kotlin.math.cos
import kotlin.math.sin

interface RotatorView {
    /**
     * Pitch in degrees.
     *
     * Minecraft convention: positive pitch looks downward, negative pitch looks upward.
     */
    val pitch: Double

    /**
     * Yaw in degrees.
     *
     * Minecraft convention: 0 faces +Z, -90 faces +X, and 90 faces -X.
     */
    val yaw: Double

    /**
     * Roll in degrees.
     */
    val roll: Double
}

fun RotatorView.toViewVector(): VectorView {
    val pitchRadians = Math.toRadians(pitch)

    val yawRadians = Math.toRadians(-yaw)
    val yawCosine = cos(yawRadians)
    val yawSine = sin(yawRadians)

    val pitchCosine = cos(pitchRadians)
    val pitchSine = sin(pitchRadians)

    return Geometry.vector(yawSine * pitchCosine, -pitchSine, yawCosine * pitchCosine)
}