/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

/** Four components, including the fourth component of homogeneous coordinates. */
interface Vector4View : Interpolatable<Vector4View> {
    val x: Double
    val y: Double
    val z: Double
    val w: Double

    /** Selects x, y, z, or w with an index from zero through three. */
    operator fun get(index: Int): Double = when (index) {
        0 -> x
        1 -> y
        2 -> z
        3 -> w
        else -> throw IllegalArgumentException()
    }

    /**
     * Returns stable values, reusing this instance when it is immutable. Source sampling is
     * sequential and does not synchronize concurrent changes or promise a distinct identity.
     */
    fun snapshot(): Vector4View = Vector4(x, y, z, w)

    override fun interpolate(target: Vector4View, alpha: Double): Vector4View = Vector4(
        x + (target.x - x) * alpha,
        y + (target.y - y) * alpha,
        z + (target.z - z) * alpha,
        w + (target.w - w) * alpha,
    )
}

operator fun Vector4View.plus(other: Vector4View): Vector4View = Vector4(x + other.x, y + other.y, z + other.z, w + other.w)
operator fun Vector4View.minus(other: Vector4View): Vector4View = Vector4(x - other.x, y - other.y, z - other.z, w - other.w)
