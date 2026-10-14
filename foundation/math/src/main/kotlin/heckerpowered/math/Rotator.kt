/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

interface RotatorView {
    /**
     * Pitch in degrees.
     */
    val pitch: Double

    /**
     * Yaw in degrees.
     */
    val yaw: Double

    /**
     * Roll in degrees.
     */
    val roll: Double

    /**
     * Returns stable values, reusing this instance when it is immutable. Source sampling is
     * sequential and does not synchronize concurrent changes or promise a distinct identity.
     */
    fun snapshot(): RotatorView = Rotator(pitch, yaw, roll)
}