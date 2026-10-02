/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.*

/**
 * Describes the world-space volume that can contribute to a view.
 *
 * Pass preparation uses this volume to reject contributions whose conservative bounds lie
 * completely outside the view. It does not test occlusion or the contents of those bounds.
 * Every plane points inward; a point on a boundary is included and a point is inside when it
 * lies on the non-negative side of every plane.
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
     * is actually visible. Invalid box coordinates or non-finite plane distances also return true.
     */
    fun intersects(box: BoxView): Boolean {
        val bounds = Geometry.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
        if (!bounds.minX.isFinite() || !bounds.minY.isFinite() || !bounds.minZ.isFinite() ||
            !bounds.maxX.isFinite() || !bounds.maxY.isFinite() || !bounds.maxZ.isFinite() ||
            bounds.minX > bounds.maxX || bounds.minY > bounds.maxY || bounds.minZ > bounds.maxZ
        ) return true

        // Check every distance before rejection: a later plane can overflow even after a finite separator.
        val distances = planes.map { plane -> maximumPlaneDistance(bounds, plane) }
        return distances.any { !it.isFinite() } || distances.all { it >= 0.0 }
    }

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
                left = halfSpacePlane(
                    matrix.m30 + matrix.m00,
                    matrix.m31 + matrix.m01,
                    matrix.m32 + matrix.m02,
                    matrix.m33 + matrix.m03,
                ),
                right = halfSpacePlane(
                    matrix.m30 - matrix.m00,
                    matrix.m31 - matrix.m01,
                    matrix.m32 - matrix.m02,
                    matrix.m33 - matrix.m03,
                ),

                // NDC y = -1 is the upper edge of the framebuffer.
                top = halfSpacePlane(
                    matrix.m30 + matrix.m10,
                    matrix.m31 + matrix.m11,
                    matrix.m32 + matrix.m12,
                    matrix.m33 + matrix.m13,
                ),
                bottom = halfSpacePlane(
                    matrix.m30 - matrix.m10,
                    matrix.m31 - matrix.m11,
                    matrix.m32 - matrix.m12,
                    matrix.m33 - matrix.m13,
                ),

                // The RHI depth interval is 0 <= z <= w.
                near = halfSpacePlane(
                    matrix.m20,
                    matrix.m21,
                    matrix.m22,
                    matrix.m23,
                ),
                far = halfSpacePlane(
                    matrix.m30 - matrix.m20,
                    matrix.m31 - matrix.m21,
                    matrix.m32 - matrix.m22,
                    matrix.m33 - matrix.m23,
                ),
            )
        }
    }
}

/** Inward boundary of `x * point.x + y * point.y + z * point.z + constant >= 0`. */
private fun halfSpacePlane(x: Double, y: Double, z: Double, constant: Double): PlaneView =
    Planes.normalized(x, y, z, -constant)

private fun maximumPlaneDistance(box: BoxView, plane: PlaneView): Double {
    val normal = plane.normal

    // Vertex furthest toward the plane's positive half-space.
    // If even this vertex is outside, every point in the AABB is outside.
    val x = if (normal.x >= 0.0) box.maxX else box.minX
    val y = if (normal.y >= 0.0) box.maxY else box.minY
    val z = if (normal.z >= 0.0) box.maxZ else box.minZ

    return normal.x * x +
            normal.y * y +
            normal.z * z -
            plane.w
}
