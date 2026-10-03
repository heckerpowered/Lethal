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
}
