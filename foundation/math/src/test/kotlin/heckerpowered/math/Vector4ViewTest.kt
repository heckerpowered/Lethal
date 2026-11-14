/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.math

import kotlin.test.*

class Vector4ViewTest {
    @Test
    fun providerAndOrdinaryFactoriesFollowTheSharedViewProtocol() {
        for (provider in listOf(FreestandingGeometryProvider, object : GeometryProvider {})) {
            val value: Vector4View = provider.vector4(1.0, 2.0, 3.0, 4.0)
            assertComponents(listOf(1.0, 2.0, 3.0, 4.0), value)
            assertSame(value, value.snapshot())
        }
        assertComponents(listOf(1.0, 2.0, 3.0, 4.0), Geometry.vector4(1.0, 2.0, 3.0, 4.0))
        assertComponents(List(4) { 0.0 }, Vector4())
        assertComponents(List(4) { 2.0 }, Vector4(2.0))
        val value: Vector4View = Vector4(1.0, 2.0, 3.0, 4.0)
        val copied = Vector4(value)
        assertNotSame(value, copied)
        assertComponents((0..3).map { value[it] }, copied)
        assertSame(value, value.snapshot())
        for (index in listOf(-1, 4, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { value[index] }
        }
    }

    @Test
    fun mutableViewsAreDetachedByBothSnapshotAndCopyConstruction() {
        val source = MutableVector4(1.0, 2.0, 3.0, 4.0)
        val view: Vector4View = source
        val snapshot = view.snapshot()
        val copied = Vector4(view)
        assertNotSame(view, snapshot)
        assertNotSame(view, copied)
        source.x = 10.0
        source.y = 20.0
        source.z = 30.0
        source.w = 40.0
        assertComponents(listOf(1.0, 2.0, 3.0, 4.0), snapshot)
        assertComponents(listOf(1.0, 2.0, 3.0, 4.0), copied)
        assertSame(snapshot, snapshot.snapshot())
        assertComponents(listOf(10.0, 20.0, 30.0, 40.0), view)
    }

    @Test
    fun defaultSnapshotSamplesEveryComponentOnceWithoutNormalizingValues() {
        val values = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, -0.0, -4.0)
        val reads = IntArray(4)
        val source = object : Vector4View {
            override val x get() = values[0].also { reads[0]++ }
            override val y get() = values[1].also { reads[1]++ }
            override val z get() = values[2].also { reads[2]++ }
            override val w get() = values[3].also { reads[3]++ }
        }
        val captured = source.snapshot()
        values.fill(100.0)
        assertContentEquals(intArrayOf(1, 1, 1, 1), reads)
        assertTrue(captured.x.isNaN())
        assertEquals(Double.POSITIVE_INFINITY, captured.y)
        assertEquals((-0.0).toBits(), captured.z.toBits())
        assertEquals(-4.0, captured.w)
    }

    @Test
    fun arithmeticAndInterpolationAcceptViewsAndProduceStableFourComponentValues() {
        val source = MutableVector4(1.0, -2.0, 3.0, -4.0)
        val left: Vector4View = source
        val right: Vector4View = object : Vector4View {
            override val x = 5.0
            override val y = 6.0
            override val z = -7.0
            override val w = 8.0
        }
        val sum: Vector4View = left + right
        val difference: Vector4View = left - right
        val interpolated: Vector4View = left.interpolate(right, .25)
        assertComponents(listOf(1.0, -2.0, 3.0, -4.0), left)
        source.w = 100.0
        assertComponents(listOf(6.0, 4.0, -4.0, 4.0), sum)
        assertComponents(listOf(-4.0, -8.0, 10.0, -12.0), difference)
        assertComponents(listOf(2.0, 0.0, .5, -1.0), interpolated)
        assertSame(sum, sum.snapshot())
    }

    private class MutableVector4(
        override var x: Double,
        override var y: Double,
        override var z: Double,
        override var w: Double,
    ) : Vector4View

    private fun assertComponents(expected: List<Double>, actual: Vector4View) {
        assertEquals(expected, (0..3).map { actual[it] })
    }
}
