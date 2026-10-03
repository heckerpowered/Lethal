/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.support.collection

import java.util.*

/**
 * Copies this collection's membership and iteration order into a list that rejects mutation.
 *
 * Elements, including null values, are retained by reference. Later structural changes to this
 * collection do not affect the returned list.
 */
internal fun <T> Collection<T>.toUnmodifiableList(): List<T> =
    Collections.unmodifiableList(ArrayList(this))

/**
 * Copies this map's entries and iteration order into a map that rejects mutation.
 *
 * Keys and values, including nulls, are retained by reference. Later structural changes to this
 * map do not affect the returned map.
 */
internal fun <K, V> Map<K, V>.toUnmodifiableMap(): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(this))
