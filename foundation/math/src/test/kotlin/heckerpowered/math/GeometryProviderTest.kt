/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
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
            val matrix = provider.matrix(
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

    @Test
    fun convenienceConstructorsPreserveCoordinatesAndDefaultArguments() {
        val minimum: VectorView = Vector(-1.0, -2.0, -3.0)
        val maximum: VectorView = Vector(4.0, 5.0, 6.0)
        assertVector(-1.0, -2.0, -3.0, minimum)
        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, Box(minimum, maximum))
        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, Box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0))

        val rotator: RotatorView = Rotator(pitch = 10.0, yaw = 20.0)
        assertEquals(10.0, rotator.pitch)
        assertEquals(20.0, rotator.yaw)
        assertEquals(0.0, rotator.roll)
        assertEquals(30.0, Rotator(10.0, 20.0, roll = 30.0).roll)

        val quaternion: QuaternionView = Quaternion(1.0, 2.0, 3.0, 4.0)
        val plane: PlaneView = Plane(5.0, 6.0, 7.0, 8.0)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), (0..3).map { quaternion[it] })
        assertEquals(listOf(5.0, 6.0, 7.0, 8.0), listOf(plane.x, plane.y, plane.z, plane.w))

        val matrix: MatrixView = Matrix(
            1.0, 2.0, 3.0, 4.0,
            5.0, 6.0, 7.0, 8.0,
            9.0, 10.0, 11.0, 12.0,
            13.0, 14.0, 15.0, 16.0,
        )
        assertEquals((1..16).map { it.toDouble() }, matrix.toList())
    }

    @Test
    fun convenienceConstructorsRetainProvidedViews() {
        val origin = Vector(1.0, 2.0, 3.0)
        val direction = Vector(4.0, 5.0, 6.0)
        val rotation = Quaternion(0.0, 0.0, 0.0, 1.0)
        val scale = Vector(7.0, 8.0, 9.0)
        val axisX = Vector(1.0, 0.0, 0.0)
        val axisY = Vector(0.0, 1.0, 0.0)
        val axisZ = Vector(0.0, 0.0, 1.0)

        val ray: RayView = Ray(origin, direction)
        val transform: TransformView = Transform(origin, rotation, scale)
        val affine: AffineTransformView = AffineTransform(axisX, axisY, axisZ, origin)
        assertSame(origin, ray.origin)
        assertSame(direction, ray.direction)
        assertSame(origin, transform.translation)
        assertSame(rotation, transform.rotation)
        assertSame(scale, transform.scale)
        assertSame(axisX, affine.axisX)
        assertSame(axisY, affine.axisY)
        assertSame(axisZ, affine.axisZ)
        assertSame(origin, affine.translation)
    }

    @Test
    fun basisFactoryPreservesAxesAcrossDefaultFreestandingAndFacadeCreation() {
        val right = Vector(2.0, 3.0, 4.0)
        val up = Vector(5.0, 6.0, 7.0)
        val forward = Vector(8.0, 9.0, 10.0)
        val defaultProvider = object : GeometryProvider {}
        val bases = listOf(
            FreestandingGeometryProvider.basis(right, up, forward),
            defaultProvider.basis(right, up, forward),
            Geometry.basis(right, up, forward),
            Basis(right, up, forward),
        )

        for (basis in bases) {
            assertSame(right, basis.right)
            assertSame(up, basis.up)
            assertSame(forward, basis.forward)
        }
    }

    @Test
    fun scalarVectorBroadcastPreservesEveryComponentBit() {
        val values = listOf(-0.0, -2.5, 42.0, Double.POSITIVE_INFINITY, Double.fromBits(0x7ff8000000000042L))
        for (value in values) {
            val vector = Vector(value = value)
            assertEquals(value.toRawBits(), vector.x.toRawBits())
            assertEquals(value.toRawBits(), vector.y.toRawBits())
            assertEquals(value.toRawBits(), vector.z.toRawBits())
        }
    }

    @Test
    fun defaultConstructorsMatchCanonicalZeroAndIdentityValues() {
        assertVector(0.0, 0.0, 0.0, Vector())
        val rotator = Rotator()
        assertEquals(listOf(0.0, 0.0, 0.0), listOf(rotator.pitch, rotator.yaw, rotator.roll))
        val quaternion = Quaternion()
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0), (0..3).map { quaternion[it] })
        assertEquals(Matrices.Identity.toList(), Matrix().toList())
        val transform = Transform()
        assertVector(0.0, 0.0, 0.0, transform.translation)
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0), (0..3).map { transform.rotation[it] })
        assertVector(1.0, 1.0, 1.0, transform.scale)
        val affine = AffineTransform()
        assertVector(1.0, 0.0, 0.0, affine.axisX)
        assertVector(0.0, 1.0, 0.0, affine.axisY)
        assertVector(0.0, 0.0, 1.0, affine.axisZ)
        assertVector(0.0, 0.0, 0.0, affine.translation)
        val basis = Basis()
        assertVector(1.0, 0.0, 0.0, basis.right)
        assertVector(0.0, 1.0, 0.0, basis.up)
        assertVector(0.0, 0.0, 1.0, basis.forward)
    }

    @Test
    fun scalarViewCopiesRetainValuesAndCreateNewProviderProducts() {
        val vector = Vector(1.5, -2.0, 3.25)
        val vectorCopy = Vector(vector = vector)
        assertNotSame(vector, vectorCopy)
        assertVector(vector, vectorCopy)
        val box = Box(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0)
        val boxCopy = Box(box = box)
        assertNotSame(box, boxCopy)
        assertBox(-1.0, -2.0, -3.0, 4.0, 5.0, 6.0, boxCopy)
        val rotator = Rotator(10.0, 20.0, 30.0)
        val rotatorCopy = Rotator(rotator = rotator)
        assertNotSame(rotator, rotatorCopy)
        assertEquals(listOf(10.0, 20.0, 30.0), listOf(rotatorCopy.pitch, rotatorCopy.yaw, rotatorCopy.roll))
        val quaternion = Quaternion(1.0, 2.0, 3.0, 4.0)
        val quaternionCopy = Quaternion(quaternion = quaternion)
        assertNotSame(quaternion, quaternionCopy)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), (0..3).map { quaternionCopy[it] })
        val plane = Plane(5.0, 6.0, 7.0, 8.0)
        val planeCopy = Plane(plane = plane)
        assertNotSame(plane, planeCopy)
        assertEquals(listOf(5.0, 6.0, 7.0, 8.0), listOf(planeCopy.x, planeCopy.y, planeCopy.z, planeCopy.w))
        val normalPlane = Plane(normal = Vector(5.0, 6.0, 7.0), w = 8.0)
        assertEquals(listOf(plane.x, plane.y, plane.z, plane.w), listOf(normalPlane.x, normalPlane.y, normalPlane.z, normalPlane.w))
        val matrix = Matrices.generate { row, column -> (row * 4 + column + 1).toDouble() }
        val matrixCopy = Matrix(matrix = matrix)
        assertNotSame(matrix, matrixCopy)
        assertEquals(matrix.toList(), matrixCopy.toList())
    }

    @Test
    fun compositeViewCopiesDetachTheirComponentValuesFromMutableSources() {
        var sourceX = 2.0
        var sourceW = 1.0
        val vector = object : VectorView {
            override val x get() = sourceX
            override val y = 3.0
            override val z = 4.0
        }
        val rotation = object : QuaternionView {
            override val x = 0.0
            override val y = 0.0
            override val z = 0.0
            override val w get() = sourceW
        }
        val ray = Ray(vector, vector)
        val transform = Transform(vector, rotation, vector)
        val affine = AffineTransform(vector, vector, vector, vector)
        val basis = Basis(vector, vector, vector)
        val rayCopy = Ray(ray = ray)
        val transformCopy = Transform(transform = transform)
        val affineCopy = AffineTransform(transform = affine)
        val basisCopy = Basis(basis = basis)
        assertNotSame(ray, rayCopy)
        assertNotSame(transform, transformCopy)
        assertNotSame(affine, affineCopy)
        assertNotSame(basis, basisCopy)

        sourceX = 100.0
        sourceW = 0.5
        assertEquals(100.0, transform.translation.x)
        assertEquals(0.5, transform.rotation.w)
        val copiedVectors = listOf(
            rayCopy.origin, rayCopy.direction,
            transformCopy.translation, transformCopy.scale,
            affineCopy.axisX, affineCopy.axisY, affineCopy.axisZ, affineCopy.translation,
            basisCopy.right, basisCopy.up, basisCopy.forward,
        )
        for (copied in copiedVectors) {
            assertNotSame(vector, copied)
            assertVector(2.0, 3.0, 4.0, copied)
        }
        assertNotSame(rotation, transformCopy.rotation)
        assertEquals(1.0, transformCopy.rotation.w)
    }
}
