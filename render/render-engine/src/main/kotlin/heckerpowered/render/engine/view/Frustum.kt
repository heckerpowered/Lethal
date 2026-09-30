/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.*

/**
 * A world-space view frustum.
 *
 * Every plane points inward. A point is inside the frustum when it lies on the
 * non-negative side of every plane.
 */
class Frustum private constructor(
    val left: PlaneView,
    val right: PlaneView,
    val top: PlaneView,
    val bottom: PlaneView,
    val near: PlaneView,
    val far: PlaneView,
) {
    private val planes = arrayOf(left, right, top, bottom, near, far)

    fun contains(point: VectorView): Boolean =
        planes.all { plane -> plane.evaluate(point) >= 0.0 }

    /**
     * Returns whether [box] may intersect this frustum.
     *
     * A false result proves that the complete box lies outside at least one
     * frustum plane. A true result does not imply that geometry inside the box
     * is actually visible.
     */
    fun intersects(box: BoxView): Boolean =
        planes.all { plane -> !isCompletelyOutside(box, plane) }

    companion object {
        /**
         * Extracts the world-space clipping planes from a world-to-clip matrix.
         *
         * The clip volume follows the RHI convention:
         *
         * ```
         * -w <= x <= w
         * -w <= y <= w
         *  0 <= z <= w
         * ```
         */
        fun fromWorldToClip(worldToClip: MatrixView): Frustum {
            val matrix = Matrices.copyOf(worldToClip)

            return Frustum(
                left = Planes.normalized(
                    matrix.m30 + matrix.m00,
                    matrix.m31 + matrix.m01,
                    matrix.m32 + matrix.m02,
                    matrix.m33 + matrix.m03,
                ),
                right = Planes.normalized(
                    matrix.m30 - matrix.m00,
                    matrix.m31 - matrix.m01,
                    matrix.m32 - matrix.m02,
                    matrix.m33 - matrix.m03,
                ),

                // NDC Y increases downward in the RHI convention.
                top = Planes.normalized(
                    matrix.m30 + matrix.m10,
                    matrix.m31 + matrix.m11,
                    matrix.m32 + matrix.m12,
                    matrix.m33 + matrix.m13,
                ),
                bottom = Planes.normalized(
                    matrix.m30 - matrix.m10,
                    matrix.m31 - matrix.m11,
                    matrix.m32 - matrix.m12,
                    matrix.m33 - matrix.m13,
                ),

                // Depth is 0..w, not -w..w.
                near = Planes.normalized(
                    matrix.m20,
                    matrix.m21,
                    matrix.m22,
                    matrix.m23,
                ),
                far = Planes.normalized(
                    matrix.m30 - matrix.m20,
                    matrix.m31 - matrix.m21,
                    matrix.m32 - matrix.m22,
                    matrix.m33 - matrix.m23,
                ),
            )
        }
    }
}

private fun isCompletelyOutside(box: BoxView, plane: PlaneView): Boolean {
    val normal = plane.normal

    // Vertex furthest toward the plane's positive half-space.
    // If even this vertex is outside, every point in the AABB is outside.
    val x = if (normal.x >= 0.0) box.maxX else box.minX
    val y = if (normal.y >= 0.0) box.maxY else box.minY
    val z = if (normal.z >= 0.0) box.maxZ else box.minZ

    return normal.x * x +
            normal.y * y +
            normal.z * z +
            plane.w < 0.0
}