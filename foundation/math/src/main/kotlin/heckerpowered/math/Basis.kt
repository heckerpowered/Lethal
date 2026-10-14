/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

/** Three linear axes in right, up, forward order; scale and shear need not be removed. */
interface BasisView {
    val right: VectorView
    val up: VectorView
    val forward: VectorView

    /**
     * Stabilizes child values recursively. Immutable children may be shared; sampling does not
     * synchronize concurrent changes or preserve mutable object-graph alias identity.
     */
    fun snapshot(): BasisView {
        val right = this.right
        val up = this.up
        val forward = this.forward
        return Basis(right.snapshot(), up.snapshot(), forward.snapshot())
    }
}
