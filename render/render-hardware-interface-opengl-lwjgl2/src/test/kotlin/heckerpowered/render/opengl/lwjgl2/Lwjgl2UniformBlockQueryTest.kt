/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.ReadOnlyBufferException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class Lwjgl2UniformBlockQueryTest {
    @Test
    fun scalarQueryPublishesOneValueIntoOneDirectSlot() {
        val destination = integers(1)
        queryUniformBlockProperty(destination) { 80 }
        assertEquals(80, destination[0])
        assertEquals(0, destination.position())
        assertEquals(1, destination.limit())
    }

    @Test
    fun scalarQueryWritesOneValueAtTheCurrentPositionAndPreservesTheMark() {
        val destination = integers(24)
        destination.position(3).limit(20).mark()
        queryUniformBlockProperty(destination) { 91 }
        assertEquals(3, destination.position())
        assertEquals(20, destination.limit())
        destination.reset()
        assertEquals(3, destination.position())
        assertEquals(List(24) { if (it == 3) 91 else -17 }, values(destination))
    }

    @Test
    fun directSliceWritesOnlyItsSelectedBackingElement() {
        val allocation = integers(12)
        allocation.position(3).limit(9)
        val destination = allocation.slice()
        destination.position(2).limit(4).mark()
        queryUniformBlockProperty(destination) { 37 }
        assertEquals(2, destination.position())
        assertEquals(4, destination.limit())
        destination.reset()
        assertEquals(3, allocation.position())
        assertEquals(9, allocation.limit())
        assertEquals(List(12) { if (it == 5) 37 else -17 }, values(allocation))
    }

    @Test
    fun heapDestinationRejectsBeforeNativeScalarGetter() {
        var called = false
        for (count in listOf(1, 16)) {
            val destination = IntBuffer.allocate(count)
            assertFailsWith<IllegalArgumentException> {
                queryUniformBlockProperty(destination) { called = true; 91 }
            }
            assertEquals(0, destination[0])
        }
        assertFalse(called)
    }

    @Test
    fun emptyDirectScalarDestinationRejectsBeforeNativeScalarGetter() {
        var called = false
        val destination = integers(1).apply { limit(0) }
        assertFailsWith<IllegalArgumentException> {
            queryUniformBlockProperty(destination) { called = true; 91 }
        }
        assertFalse(called)
        assertEquals(0, destination.position())
        assertEquals(0, destination.limit())
    }

    @Test
    fun readOnlyDestinationRejectsBeforeNativeScalarGetter() {
        var called = false
        val destination = integers(1).asReadOnlyBuffer()
        assertFailsWith<ReadOnlyBufferException> {
            queryUniformBlockProperty(destination) { called = true; 91 }
        }
        assertFalse(called)
        assertEquals(-17, destination[0])
    }

    @Test
    fun nativeScalarGetterFailureDoesNotChangeTheDestination() {
        val destination = integers(4)
        destination.position(1).limit(3)
        val failure = AssertionError("native query failed")
        val observed = assertFailsWith<AssertionError> {
            queryUniformBlockProperty(destination) { throw failure }
        }
        assertSame(failure, observed)
        assertEquals(1, destination.position())
        assertEquals(3, destination.limit())
        assertEquals(List(4) { -17 }, values(destination))
    }

    private fun integers(count: Int): IntBuffer = ByteBuffer.allocateDirect(count * Int.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asIntBuffer().apply {
            for (index in 0 until count) put(index, -17)
        }

    private fun values(buffer: IntBuffer): List<Int> {
        val full = buffer.duplicate().apply { clear() }
        return List(full.capacity()) { full[it] }
    }
}
