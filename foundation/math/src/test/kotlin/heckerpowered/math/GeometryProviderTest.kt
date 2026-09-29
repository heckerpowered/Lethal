/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class GeometryProviderTest {
    @Test
    fun freestandingProviderCreatesEveryGeometryRepresentation() {
        val vector = FreestandingGeometryProvider.vector(1.0, 2.0, 3.0)
        val box = FreestandingGeometryProvider.box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)
        val rotator = FreestandingGeometryProvider.rotator(10.0, 20.0, 30.0)
        val ray = FreestandingGeometryProvider.ray(vector, Vectors.UnitZ)

        assertVector(1.0, 2.0, 3.0, vector)
        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, box)
        assertEquals(expected = 10.0, actual = rotator.pitch)
        assertEquals(expected = 20.0, actual = rotator.yaw)
        assertEquals(expected = 30.0, actual = rotator.roll)
        assertSame(expected = vector, actual = ray.origin)
        assertSame(expected = Vectors.UnitZ, actual = ray.direction)
    }

    @Test
    fun defaultProviderMethodsUseFreestandingRepresentations() {
        val provider = object : GeometryProvider {}
        val minimum = provider.vector(-1.0, -2.0, -3.0)
        val maximum = provider.vector(4.0, 5.0, 6.0)

        val box = provider.box(minimum, maximum)
        val rotator = provider.rotator(10.0, 20.0)
        val ray = provider.ray(minimum, maximum)

        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, box)
        assertEquals(expected = 0.0, actual = rotator.roll)
        assertVector(-1.0, -2.0, -3.0, minimum)
        assertSame(minimum, ray.origin)
        assertSame(maximum, ray.direction)
    }

    @Test
    fun geometryFacadeConstructsValuesThroughItsProvider() {
        val minimum = Geometry.vector(-1.0, -2.0, -3.0)
        val maximum = Geometry.vector(4.0, 5.0, 6.0)
        val ray = Geometry.ray(minimum, maximum)

        assertVector(-1.0, -2.0, -3.0, minimum)
        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, Geometry.box(minimum, maximum))
        assertEquals(expected = 0.0, actual = Geometry.rotator(10.0, 20.0).roll)
        assertSame(expected = minimum, actual = ray.origin)
        assertSame(expected = maximum, actual = ray.direction)
        assertSame(expected = FreestandingGeometryProvider, actual = GeometryProvider.Freestanding)
    }

    @Test
    fun automaticProviderFallsBackWhenNoHostProviderIsInstalled() {
        assertNull(GeometryProvider.Hosting)
        assertSame(expected = GeometryProvider.Freestanding, actual = GeometryProvider.Auto)
        assertSame(expected = GeometryProvider.Auto, actual = Geometry.Provider)
    }

    @Test
    fun providersConstructNewGeometryTypesWithoutAHost() {
        for (provider in listOf(FreestandingGeometryProvider, object : GeometryProvider {})) {
            val rotation = provider.quaternion(0.0, 0.0, 0.0, 1.0)
            val translation = provider.vector(2.0, 3.0, 4.0)
            val scale = provider.vector(5.0, 6.0, 7.0)
            val transform = provider.transform(translation, rotation, scale)
            val affine = provider.affineTransform(Vectors.UnitX, Vectors.UnitY, Vectors.UnitZ, translation)
            val matrix = provider.matrix4(
                1.0, 2.0, 3.0, 4.0,
                5.0, 6.0, 7.0, 8.0,
                9.0, 10.0, 11.0, 12.0,
                13.0, 14.0, 15.0, 16.0
            )
            assertEquals(listOf(0.0, 0.0, 0.0, 1.0), (0..3).map { rotation[it] })
            assertSame(rotation, transform.rotation)
            assertSame(translation, transform.translation)
            assertSame(scale, transform.scale)
            assertVector(7.0, 9.0, 11.0, transform.transformPosition(Vectors.One))
            assertSame(translation, affine.translation)
            assertVector(3.0, 4.0, 5.0, affine.transformPosition(Vectors.One))
            assertEquals((1..16).map { it.toDouble() }, matrix.toList())
        }
    }

}
