/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.bridge.math

interface Basis3dView {
    val right: VectorView
    val up: VectorView
    val forward: VectorView
}

data class Basis3d(
    override val right: VectorView,
    override val up: VectorView,
    override val forward: VectorView,
) : Basis3dView