/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.support.collection

import kotlin.test.*

class UnmodifiableMapTest {
    @Test
    fun snapshotPreservesEntryOrderNullsAndReferencesAfterSourceChanges() {
        val key = StringBuilder("key")
        val value = StringBuilder("before")
        val source = linkedMapOf<Any?, StringBuilder?>(key to value, null to null, "last" to value)
        val snapshot: Map<Any?, StringBuilder?> = source.toUnmodifiableMap()
        source[key] = StringBuilder("replacement")
        source.remove(null)
        source["new"] = null
        source.clear()
        value.append(" after")

        assertEquals(listOf(key, null, "last"), snapshot.keys.toList())
        assertSame(key, snapshot.keys.first())
        assertSame(value, snapshot[key])
        assertSame(value, snapshot["last"])
        assertTrue(snapshot.containsKey(null))
        assertNull(snapshot[null])
        assertEquals("before after", snapshot[key].toString())
        assertEquals(listOf(value, null, value), snapshot.values.toList())
        assertEquals(listOf(key to value, null to null, "last" to value), snapshot.entries.map { it.key to it.value })
    }

    @Test
    fun snapshotRejectsMutationThroughMapEntriesViewsAndIterators() {
        val snapshot = linkedMapOf("first" to 1, "second" to 2).toUnmodifiableMap() as MutableMap<String, Int>
        assertFailsWith<UnsupportedOperationException> { snapshot["third"] = 3 }
        assertFailsWith<UnsupportedOperationException> { snapshot.putAll(mapOf("third" to 3)) }
        assertFailsWith<UnsupportedOperationException> { snapshot.remove("first") }
        assertFailsWith<UnsupportedOperationException> { snapshot.clear() }
        assertFailsWith<UnsupportedOperationException> { snapshot.entries.first().setValue(3) }
        assertFailsWith<UnsupportedOperationException> { snapshot.entries.remove(snapshot.entries.first()) }
        assertFailsWith<UnsupportedOperationException> { snapshot.keys.remove("first") }
        assertFailsWith<UnsupportedOperationException> { snapshot.values.remove(1) }
        assertFailsWith<UnsupportedOperationException> { snapshot.entries.clear() }
        assertFailsWith<UnsupportedOperationException> { snapshot.keys.clear() }
        assertFailsWith<UnsupportedOperationException> { snapshot.values.clear() }
        val entries = snapshot.entries.iterator()
        entries.next()
        assertFailsWith<UnsupportedOperationException> { entries.remove() }
        val keys = snapshot.keys.iterator()
        keys.next()
        assertFailsWith<UnsupportedOperationException> { keys.remove() }
        val values = snapshot.values.iterator()
        values.next()
        assertFailsWith<UnsupportedOperationException> { values.remove() }
        assertEquals(linkedMapOf("first" to 1, "second" to 2), snapshot)
    }

    @Test
    fun emptySnapshotStillRejectsMutation() {
        val snapshot = emptyMap<String?, Int?>().toUnmodifiableMap() as MutableMap<String?, Int?>
        assertFailsWith<UnsupportedOperationException> { snapshot[null] = null }
        assertFailsWith<UnsupportedOperationException> { snapshot.clear() }
        assertTrue(snapshot.isEmpty())
    }
}
