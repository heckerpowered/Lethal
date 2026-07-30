/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.client.render

import heckerpowered.bridge.adapter.entity.EntityAccess
import heckerpowered.bridge.math.*

/**
 * Interpolates this entity's position for the current render tick.
 */
fun EntityAccess.interpolatePosition(partialTick: Float): VectorView {
    return previousPosition.interpolate(position, partialTick.toDouble())
}

/**
 * Interpolates this entity's eye position for the current render tick.
 */
fun EntityAccess.interpolateEyePosition(partialTick: Float): VectorView {
    return interpolatePosition(partialTick) + MinecraftDirections.Up * eyeHeight
}

/**
 * Interpolates this entity's rotation for the current render tick.
 */
fun EntityAccess.interpolateRotation(partialTick: Float): RotatorView {
    val alpha = partialTick.toDouble()
    val pitch = interpolate(previousPitch, pitch, alpha)
    val yaw = interpolate(previousYaw, yaw, alpha)
    return Geometry.rotator(pitch, yaw)
}

private fun interpolate(previousValue: Double, currentValue: Double, alpha: Double): Double {
    return previousValue + (currentValue - previousValue) * alpha
}
