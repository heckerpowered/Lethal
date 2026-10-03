/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.render

import heckerpowered.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorldViewProjectionTest {
    @Test
    fun mapsHostNearAndFarPlanesToRhiDepth() {
        val matrix = worldViewProjectionMatrix(perspective(), identity(), Vector(0.0, 0.0, 0.0))

        assertEquals(0.0, projected(matrix, 0.0, 0.0, -1.0, 2), 0.000001)
        assertEquals(1.0, projected(matrix, 0.0, 0.0, -10.0, 2), 0.000001)
    }

    @Test
    fun appliesCameraTranslationBeforeHostViewAndProjection() {
        val modelView = floatArrayOf(
            0F, 1F, 0F, 0F,
            -1F, 0F, 0F, 0F,
            0F, 0F, 1F, 0F,
            0F, -1F, 0F, 1F,
        )
        val matrix = worldViewProjectionMatrix(perspective(), modelView, Vector(10.0, 20.0, 30.0))

        assertEquals(-1.0, projected(matrix, 11.0, 21.0, 28.0, 0), 0.000001)
        assertEquals(0.0, projected(matrix, 11.0, 21.0, 28.0, 1), 0.000001)
        assertEquals(5.0 / 9.0, projected(matrix, 11.0, 21.0, 28.0, 2), 0.00001)
    }

    @Test
    fun convertsHostYUpToCanonicalYDownBeforeBackendLowering() {
        val matrix = worldViewProjectionMatrix(identity(), identity(), Vectors.Zero)

        assertEquals(-0.5, projected(matrix, 0.0, 0.5, 0.0, 1))
        assertEquals(0.5, projected(matrix, 0.0, 0.5, 0.0, 2))
    }

    @Test
    fun preservesFractionalCameraTranslationAtFarWorldCoordinates() {
        val camera = Vector(16_777_216.75, 0.0, 0.0)
        val matrix = worldViewProjectionMatrix(perspective(), identity(), camera)

        assertEquals(-33_554_433.5, matrix[0, 3])
        assertEquals(0.125, projected(matrix, camera.x + 0.125, 0.0, -2.0, 0))
    }

    @Test
    fun nearAndFarWorldPlacementsRetainTheSameLocalProjection() {
        val localPlacement = AffineTransforms.of(
            axisX = Vector(0.0, 2.0, 0.0),
            axisY = Vector(-0.5, 0.0, 0.0),
            axisZ = Vector(0.25, 0.0, 1.0),
            translation = Vector(0.125, -0.25, -3.0),
        )
        val localPoint = Vector(0.2, -0.5, -0.25)
        val cameras = listOf(Vector(0.75, -0.5, 0.25), Vector(16_777_216.75, -16_777_216.5, 30_000_000.25))
        val relativePoint = localPlacement.transformPosition(localPoint)
        val reference = worldViewProjectionMatrix(perspective(), identity(), Vectors.Zero)

        for (camera in cameras) {
            val worldPlacement = AffineTransforms.fromTranslation(camera) * localPlacement
            val clipFromLocal = worldViewProjectionMatrix(perspective(), identity(), camera) * worldPlacement.toMatrix4()
            for (row in 0..2) {
                assertEquals(
                    projected(reference, relativePoint.x, relativePoint.y, relativePoint.z, row),
                    projected(clipFromLocal, localPoint.x, localPoint.y, localPoint.z, row),
                    0.0000001,
                )
            }
        }
    }

    @Test
    fun preservesHostReverseDepthAtNearAndFarWorldCoordinates() {
        val reverseProjection = perspective().also { values ->
            for (column in 0..3) values[column * 4 + 2] = -values[column * 4 + 2]
        }
        for (camera in listOf(Vectors.Zero, Vector(16_777_216.75, 16_777_216.5, 30_000_000.25))) {
            val matrix = worldViewProjectionMatrix(reverseProjection, identity(), camera)
            assertEquals(1.0, projected(matrix, camera.x, camera.y, camera.z - 1.0, 2), 0.000001)
            assertEquals(0.0, projected(matrix, camera.x, camera.y, camera.z - 10.0, 2), 0.000001)
        }
    }

    @Test
    fun rejectsIncompleteHostMatrices() {
        val camera = Vector(0.0, 0.0, 0.0)
        assertFailsWith<IllegalArgumentException> { worldViewProjectionMatrix(FloatArray(15), identity(), camera) }
        assertFailsWith<IllegalArgumentException> { worldViewProjectionMatrix(identity(), FloatArray(17), camera) }
    }

    private fun projected(matrix: MatrixView, x: Double, y: Double, z: Double, row: Int): Double {
        val clip = matrix[row, 0] * x + matrix[row, 1] * y + matrix[row, 2] * z + matrix[row, 3]
        val weight = matrix[3, 0] * x + matrix[3, 1] * y + matrix[3, 2] * z + matrix[3, 3]
        return clip / weight
    }

    private fun perspective() = floatArrayOf(
        2F, 0F, 0F, 0F,
        0F, 3F, 0F, 0F,
        0F, 0F, -11F / 9F, -1F,
        0F, 0F, -20F / 9F, 0F,
    )

    private fun identity() = floatArrayOf(
        1F, 0F, 0F, 0F,
        0F, 1F, 0F, 0F,
        0F, 0F, 1F, 0F,
        0F, 0F, 0F, 1F,
    )
}
