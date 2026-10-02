/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.support.collection

import kotlin.test.*

class CopyOnWriteListTest {
    @Test
    fun snapshotsRetainEveryGenerationWhileAdditionsContinue() {
        val values = CopyOnWriteList<Int>()
        val empty = values.snapshot()
        assertSame(empty, values.snapshot())
        assertEquals(0, values.size)

        values.add(1)
        val first = values.snapshot()
        val iterator = first.iterator()
        assertSame(first, values.snapshot())

        values.add(2)
        values.add(3)
        val second = values.snapshot()
        assertSame(second, values.snapshot())
        values.add(4)
        val third = values.snapshot()

        assertTrue(empty.isEmpty())
        assertEquals(listOf(1), first)
        assertEquals(listOf(1, 2, 3), second)
        assertEquals(listOf(1, 2, 3, 4), third)
        assertEquals(4, values.size)
        assertEquals(1, iterator.next())
        assertFalse(iterator.hasNext())
    }

    @Test
    fun snapshotsRejectMutationThroughListsAndIterators() {
        val values = CopyOnWriteList<Int>()
        val empty = values.snapshot() as MutableList<Int>
        assertFailsWith<UnsupportedOperationException> { empty.add(1) }
        values.add(1)
        val snapshot = values.snapshot() as MutableList<Int>

        assertFailsWith<UnsupportedOperationException> { snapshot.add(2) }
        assertFailsWith<UnsupportedOperationException> { snapshot[0] = 2 }
        assertFailsWith<UnsupportedOperationException> { snapshot.removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { snapshot.clear() }
        assertFailsWith<UnsupportedOperationException> { snapshot.subList(0, 1).clear() }
        val iterator = snapshot.listIterator()
        assertEquals(1, iterator.next())
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        assertFailsWith<UnsupportedOperationException> { iterator.set(2) }
        assertFailsWith<UnsupportedOperationException> { iterator.add(2) }
        assertEquals(listOf(1), values.snapshot())
    }

    @Test
    fun snapshotsRetainValuesByReference() {
        val value = StringBuilder("before")
        val values = CopyOnWriteList<StringBuilder>()
        values.add(value)
        val snapshot = values.snapshot()
        value.append(" after")
        values.add(StringBuilder("another"))

        assertSame(value, snapshot.single())
        assertEquals("before after", snapshot.single().toString())
        assertEquals(1, snapshot.size)
    }
}
