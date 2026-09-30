/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.mesh

import heckerpowered.math.BoxView

interface Mesh {
    /**
     * Conservative local-space bounds containing this mesh's geometry.
     *
     * These bounds authorize visibility rejection only for this mesh's own
     * rendering contribution. They must never be used to suppress sibling
     * render elements emitted by the same object.
     *
     * Rendering techniques that can move geometry outside these bounds, such
     * as vertex displacement, must either provide correspondingly expanded
     * bounds or disable bounds-based culling for that contribution.
     */
    val bounds: BoxView
}