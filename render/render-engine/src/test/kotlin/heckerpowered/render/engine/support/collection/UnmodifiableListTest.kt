/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.support.collection

import kotlin.test.*

class UnmodifiableListTest {
    @Test
    fun snapshotCopiesMembershipAndRetainsNullsAndElementReferences() {
        val value = StringBuilder("before")
        val source = arrayListOf(value, null, value)
        val snapshot = source.toUnmodifiableList()
        source.clear()
        value.append(" after")

        assertEquals(3, snapshot.size)
        assertSame(value, snapshot[0])
        assertNull(snapshot[1])
        assertSame(value, snapshot[2])
        assertEquals("before after", snapshot[0].toString())
    }

    @Test
    fun snapshotsRejectMutationThroughListsAndIterators() {
        val empty = emptyList<Int>().toUnmodifiableList() as MutableList<Int>
        assertFailsWith<UnsupportedOperationException> { empty.add(1) }
        val snapshot = listOf(1, 2).toUnmodifiableList() as MutableList<Int>
        assertFailsWith<UnsupportedOperationException> { snapshot.add(3) }
        assertFailsWith<UnsupportedOperationException> { snapshot[0] = 3 }
        assertFailsWith<UnsupportedOperationException> { snapshot.removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { snapshot.clear() }
        assertFailsWith<UnsupportedOperationException> { snapshot.subList(0, 1).clear() }
        val iterator = snapshot.listIterator()
        assertEquals(1, iterator.next())
        assertFailsWith<UnsupportedOperationException> { iterator.remove() }
        assertFailsWith<UnsupportedOperationException> { iterator.set(3) }
        assertFailsWith<UnsupportedOperationException> { iterator.add(3) }
        assertEquals(listOf(1, 2), snapshot)
    }
}
