/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.foundation.math.Geometry
import heckerpowered.foundation.math.VectorView

object MinecraftDirections {
    /**
     * Up vector: (0, 1, 0).
     */
    val Up: VectorView = Geometry.vector(0.0, 1.0, 0.0)

    /**
     * Down vector: (0, -1, 0).
     */
    val Down: VectorView = Geometry.vector(0.0, -1.0, 0.0)

    /**
     * Minecraft forward vector: (0, 0, 1).
     *
     * This matches Minecraft's view direction when pitch = 0 and yaw = 0.
     */
    val Forward: VectorView = Geometry.vector(0.0, 0.0, 1.0)

    /**
     * Minecraft backward vector: (0, 0, -1).
     */
    val Backward: VectorView = Geometry.vector(0.0, 0.0, -1.0)

    /**
     * Minecraft right vector: (-1, 0, 0).
     *
     * In Minecraft, positive yaw rotates from +Z toward -X.
     */
    val Right: VectorView = Geometry.vector(-1.0, 0.0, 0.0)

    /**
     * Minecraft left vector: (1, 0, 0).
     */
    val Left: VectorView = Geometry.vector(1.0, 0.0, 0.0)
}