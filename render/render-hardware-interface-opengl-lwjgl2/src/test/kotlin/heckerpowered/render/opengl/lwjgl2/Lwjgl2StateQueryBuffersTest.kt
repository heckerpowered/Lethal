/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import org.lwjgl.BufferChecks
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ReadOnlyBufferException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class Lwjgl2StateQueryBuffersTest {
    @Test
    fun eachScalarUsesDirectSixteenSlotScratchAndRetainsItBetweenCalls() {
        val queries = Lwjgl2StateQueryBuffers()
        val floats = bytes(4).asFloatBuffer()
        var floatScratch: FloatBuffer? = null
        repeat(3) { value ->
            queries.query(floats) {
                BufferChecks.checkBuffer(it, 16)
                floatScratch?.let { previous -> assertSame(previous, it) }
                floatScratch = it
                assertEquals(16, it.remaining())
                it.put(0, value.toFloat())
            }
            assertEquals(value.toFloat(), floats[0])
        }
        val integers = bytes(4).asIntBuffer()
        var intScratch: IntBuffer? = null
        repeat(3) { value ->
            queries.query(integers) {
                BufferChecks.checkBuffer(it, 16)
                intScratch?.let { previous -> assertSame(previous, it) }
                intScratch = it
                it.put(0, value)
            }
            assertEquals(value, integers[0])
        }
        val doubles = bytes(8).asDoubleBuffer()
        var doubleScratch: DoubleBuffer? = null
        repeat(3) { value ->
            queries.query(doubles) {
                BufferChecks.checkBuffer(it, 16)
                doubleScratch?.let { previous -> assertSame(previous, it) }
                doubleScratch = it
                it.put(0, value.toDouble())
            }
            assertEquals(value.toDouble(), doubles[0])
        }
    }

    @Test
    fun selectedFloatSlicePublishesOnlyRequestedElementsAndPreservesCursorAndMark() {
        val backing = bytes(32).asFloatBuffer().apply { for (index in 0..<capacity()) put(index, -17f) }
        backing.position(2).limit(7)
        val destination = backing.slice().apply { position(1).limit(3).mark() }
        Lwjgl2StateQueryBuffers().query(destination) {
            it.put(0, 8f)
            it.put(1, 9f)
            it.put(2, 100f)
        }
        assertEquals(1, destination.position())
        assertEquals(3, destination.limit())
        destination.reset()
        assertEquals(2, backing.position())
        assertEquals(7, backing.limit())
        val all = backing.duplicate().apply { clear() }
        assertEquals(List(8) { when (it) { 3 -> 8f; 4 -> 9f; else -> -17f } }, List(8) { all[it] })
    }

    @Test
    fun integerAndDoubleWindowsPreserveUnrequestedValuesAndNativeUnwrittenSlots() {
        val integers = bytes(20).asIntBuffer().apply {
            for (index in 0..<capacity()) put(index, -23)
            position(1).limit(4).mark()
        }
        val doubles = bytes(40).asDoubleBuffer().apply {
            for (index in 0..<capacity()) put(index, -23.0)
            position(1).limit(4).mark()
        }
        val queries = Lwjgl2StateQueryBuffers()
        queries.query(integers) { it.put(0, 91) }
        queries.query(doubles) { it.put(0, 91.0) }
        assertEquals(1, integers.position())
        assertEquals(4, integers.limit())
        integers.reset()
        assertEquals(1, doubles.position())
        assertEquals(4, doubles.limit())
        doubles.reset()
        assertEquals(listOf(-23, 91, -23, -23, -23), List(5) { integers.duplicate().apply { clear() }[it] })
        assertEquals(listOf(-23.0, 91.0, -23.0, -23.0, -23.0), List(5) { doubles.duplicate().apply { clear() }[it] })
        queries.query(integers) { }
        queries.query(doubles) { }
        assertEquals(91, integers[1])
        assertEquals(91.0, doubles[1])
    }

    @Test
    fun adequateDirectStoragePassesThroughWithoutChangingSelectedRange() {
        val queries = Lwjgl2StateQueryBuffers()
        val floats = bytes(80).asFloatBuffer().apply { position(2).limit(18).mark() }
        queries.query(floats) {
            assertSame(floats, it)
            BufferChecks.checkBuffer(it, 16)
            it.put(2, 7f)
        }
        assertEquals(2, floats.position())
        assertEquals(18, floats.limit())
        floats.reset()
        assertEquals(7f, floats[2])
        val integers = bytes(80).asIntBuffer()
        queries.query(integers) { assertSame(integers, it) }
        val doubles = bytes(160).asDoubleBuffer()
        queries.query(doubles) { assertSame(doubles, it) }
    }

    @Test
    fun invalidDestinationRejectsBeforeAnyNativeQuery() {
        val queries = Lwjgl2StateQueryBuffers()
        var called = false
        assertFailsWith<IllegalArgumentException> { queries.query(FloatBuffer.allocate(1)) { called = true } }
        assertFailsWith<IllegalArgumentException> { queries.query(IntBuffer.allocate(16)) { called = true } }
        assertFailsWith<IllegalArgumentException> { queries.query(DoubleBuffer.allocate(1)) { called = true } }
        assertFailsWith<IllegalArgumentException> {
            queries.query(bytes(4).asFloatBuffer().apply { limit(0) }) { called = true }
        }
        assertFailsWith<ReadOnlyBufferException> {
            queries.query(bytes(4).asFloatBuffer().asReadOnlyBuffer()) { called = true }
        }
        assertFailsWith<ReadOnlyBufferException> {
            queries.query(bytes(4).asIntBuffer().asReadOnlyBuffer()) { called = true }
        }
        assertFailsWith<ReadOnlyBufferException> {
            queries.query(bytes(8).asDoubleBuffer().asReadOnlyBuffer()) { called = true }
        }
        assertFalse(called)
    }

    @Test
    fun nativeFailureDoesNotPublishScratchOrChangeDestinationState() {
        val destination = bytes(12).asFloatBuffer().apply {
            put(0, -17f)
            put(1, -17f)
            put(2, -17f)
            position(1).limit(2).mark()
        }
        val failure = AssertionError("native getter failure")
        val observed = assertFailsWith<AssertionError> {
            Lwjgl2StateQueryBuffers().query(destination) {
                it.put(0, 91f)
                throw failure
            }
        }
        assertSame(failure, observed)
        assertEquals(-17f, destination[1])
        assertEquals(1, destination.position())
        assertEquals(2, destination.limit())
        destination.reset()
    }

    private fun bytes(size: Int) = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
}
