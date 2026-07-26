/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.adapter.world.raycast

/**
 * Selects the host shape used to test blocks along a ray.
 */
enum class BlockRaycastShape {
    /**
     * The shape that blocks use to obstruct entity movement.
     */
    Collision,

    /**
     * The shape that blocks expose for selection, including non-colliding plants.
     */
    Outline,
}
