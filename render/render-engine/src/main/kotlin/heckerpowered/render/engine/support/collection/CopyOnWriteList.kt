/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.support.collection

import java.util.*

/**
 * Collects values while retaining stable, read-only snapshots of their membership and order.
 *
 * Snapshots share storage until the next addition, which copies the membership before appending.
 * Repeated snapshots without additions return the same list in constant time. Values themselves
 * are retained by reference; their contents are not frozen.
 *
 * Addition and snapshotting require external synchronization when used from multiple threads.
 */
internal class CopyOnWriteList<T> {
    private var storage = ArrayList<T>()
    private var published: List<T>? = null

    val size: Int
        get() = storage.size

    fun add(value: T) {
        if (published != null) {
            val detached = ArrayList<T>(storage.size + 1)
            for (existingValue in storage) detached.add(existingValue)
            storage = detached
            published = null
        }
        storage.add(value)
    }

    fun snapshot(): List<T> =
        published ?: Collections.unmodifiableList(storage).also { published = it }
}
