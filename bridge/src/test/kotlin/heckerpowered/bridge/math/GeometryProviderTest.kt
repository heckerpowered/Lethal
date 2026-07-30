/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.bridge.math

import heckerpowered.bridge.FreestandingRepresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class GeometryProviderTest {
    @Test
    fun freestandingProviderCreatesEveryGeometryRepresentation() {
        val vector = FreestandingGeometryProvider.vector(1.0, 2.0, 3.0)
        val box = FreestandingGeometryProvider.box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)
        val rotator = FreestandingGeometryProvider.rotator(10.0, 20.0, 30.0)
        val ray = FreestandingGeometryProvider.ray(vector, Vectors.UnitZ)

        assertIs<FreestandingRepresentation>(vector)
        assertIs<FreestandingRepresentation>(box)
        assertIs<FreestandingRepresentation>(rotator)
        assertIs<FreestandingRepresentation>(ray)
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

        assertIs<FreestandingRepresentation>(minimum)
        assertIs<FreestandingRepresentation>(box)
        assertIs<FreestandingRepresentation>(rotator)
        assertIs<FreestandingRepresentation>(ray)
        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, box)
        assertEquals(expected = 0.0, actual = rotator.roll)
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
}
