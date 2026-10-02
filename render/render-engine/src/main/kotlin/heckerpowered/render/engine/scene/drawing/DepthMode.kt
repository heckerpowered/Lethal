/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene.drawing

/**
 * Describes how a contribution should relate to scene depth.
 *
 * Scene uses the consuming pass's depth comparison. SeeThrough skips the scene-depth test;
 * AlwaysOnTop requests a separate phase with its own cleared depth, allowing contributions in
 * that phase to occlude one another. A pass policy must support or resolve the selected mode;
 * this declaration alone does not create a phase or change compositing order.
 */
enum class DepthMode {
    Scene,
    SeeThrough,
    AlwaysOnTop
}
