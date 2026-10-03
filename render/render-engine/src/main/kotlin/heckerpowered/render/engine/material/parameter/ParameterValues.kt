/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.material.parameter

import heckerpowered.render.engine.support.collection.toUnmodifiableMap

/**
 * Keeps a named set of shader inputs independent of their eventual binding locations.
 *
 * The map is copied at construction and exposed without mutation. Numeric values contain host
 * snapshots; resource values still reference live GPU storage. View, geometry, and appearance
 * providers combine their maps during preparation, with duplicate names rejected rather than
 * silently choosing one provider's value.
 */
class ParameterValues(values: Map<ParameterName, ParameterValue> = emptyMap()) {
    val values: Map<ParameterName, ParameterValue> = values.toUnmodifiableMap()

    /** Returns a new set with [name] added or replaced; this set remains unchanged. */
    fun replacing(name: String, value: ParameterValue): ParameterValues {
        val copy = LinkedHashMap(values)
        copy[ParameterName(name)] = value
        return ParameterValues(copy)
    }

    /**
     * Combines disjoint providers without changing either set.
     *
     * @throws IllegalArgumentException if any name occurs in both sets, even with the same value.
     */
    fun mergedWith(other: ParameterValues): ParameterValues {
        val overlap = values.keys.intersect(other.values.keys)
        require(overlap.isEmpty()) { "Parameter providers overlap: $overlap" }
        return ParameterValues(LinkedHashMap(values).apply { putAll(other.values) })
    }

    fun requireNumeric(name: ParameterName): NumericParameterValue =
        requireNotNull(require(name) as? NumericParameterValue) { "${name.value} requires numeric bytes" }

    fun require(name: ParameterName): ParameterValue =
        requireNotNull(values[name]) { "Missing shader parameter ${name.value}" }
}
