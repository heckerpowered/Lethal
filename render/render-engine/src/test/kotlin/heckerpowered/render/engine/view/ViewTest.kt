/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.view

import heckerpowered.math.Vector
import heckerpowered.math.MatrixView
import kotlin.test.*

class ViewTest {
    @Test
    fun projectionDistancesMatrixClipAndFrustumRemainOneSnapshotAfterSourceChanges() {
        val original = PerspectiveProjection.degrees(90.0, 1.0, 1.0, 10.0)
        var horizontalScale = original.matrix.m00
        var near = original.nearPlane
        var far = original.farPlane
        var nearReads = 0
        var farReads = 0
        var matrixReads = 0
        val sourceMatrix = object : MatrixView {
            override val m00 get() = horizontalScale
            override val m01 = original.matrix.m01
            override val m02 = original.matrix.m02
            override val m03 = original.matrix.m03

            override val m10 = original.matrix.m10
            override val m11 = original.matrix.m11
            override val m12 = original.matrix.m12
            override val m13 = original.matrix.m13

            override val m20 = original.matrix.m20
            override val m21 = original.matrix.m21
            override val m22 = original.matrix.m22
            override val m23 = original.matrix.m23

            override val m30 = original.matrix.m30
            override val m31 = original.matrix.m31
            override val m32 = original.matrix.m32
            override val m33 = original.matrix.m33
        }
        val source = object : Projection {
            override val nearPlane get() = near.also { nearReads++ }
            override val farPlane get() = far.also { farReads++ }
            override val matrix get() = sourceMatrix.also { matrixReads++ }
        }
        val frame = ViewFrame.of(Vector(0.0, 0.0, 0.0), Vector(1.0, 0.0, 0.0), Vector(0.0, 1.0, 0.0), Vector(0.0, 0.0, 1.0))
        val view = View(frame, source)
        val point = Vector(1.5, 0.0, 2.0)
        assertTrue(view.frustum.contains(point))

        horizontalScale = 4.0
        near = 3.0
        far = 30.0

        assertNotSame(source, view.projection)
        assertNotSame(sourceMatrix, view.projection.matrix)
        assertEquals(original.nearPlane, view.projection.nearPlane)
        assertEquals(original.farPlane, view.projection.farPlane)
        assertEquals(original.matrix.m00, view.projection.matrix.m00)
        assertEquals(view.projection.matrix.m00, view.worldToClip.m00)
        assertTrue(view.frustum.contains(point))
        assertEquals(1, nearReads)
        assertEquals(1, farReads)
        assertEquals(1, matrixReads)
    }
}
