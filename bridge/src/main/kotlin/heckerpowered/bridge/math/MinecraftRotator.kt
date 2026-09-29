/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.foundation.math.Geometry
import heckerpowered.foundation.math.RotatorView
import heckerpowered.foundation.math.VectorView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

fun VectorView.toRotator(): RotatorView {
    val horizontalLength = sqrt(x * x + z * z)
    return Geometry.rotator(
        Math.toDegrees(atan2(-y, horizontalLength)),
        Math.toDegrees(-atan2(x, z)),
        0.0
    )
}