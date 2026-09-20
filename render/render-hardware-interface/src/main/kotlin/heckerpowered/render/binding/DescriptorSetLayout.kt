/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.binding

import java.util.*

/**
 * Describes the resource slots that can be supplied together as one descriptor set.
 *
 * A material layout might declare a sampled texture at binding 0 and a sampler at binding 1.
 * Brick and wood materials can then supply different images using the same layout. The layout
 * describes what is accepted, not which material resources are currently selected.
 *
 * Binding numbers are explicit and may contain gaps. They are unique across the whole set,
 * even when two declarations name different shader stages. Separate sets can reuse the same
 * binding numbers; their positions in the pipeline layout distinguish them.
 *
 * This is an immutable, device-independent description, not an allocated descriptor set or a
 * separately closeable resource. Bindings are copied and sorted by number. Structural equality
 * ignores declaration order and [label], allowing equivalent declarations to identify one
 * resource interface. It does not by itself prove compatibility with a particular shader.
 *
 * @throws IllegalArgumentException if a binding number is declared more than once.
 */
class DescriptorSetLayout(
    bindings: List<DescriptorBindingLayout> = emptyList(),
    val label: String = "",
) {
    val bindings: List<DescriptorBindingLayout> =
        Collections.unmodifiableList(bindings.sortedBy { it.binding })

    init {
        require(this.bindings.zipWithNext().none { (left, right) -> left.binding == right.binding }) { "Descriptor binding numbers must be unique within a set" }
    }

    /** Looks up an explicit binding number. An omitted number is an absent slot, not a null resource. */
    fun findBinding(binding: Int): DescriptorBindingLayout? = bindings.firstOrNull { it.binding == binding }

    override fun equals(other: Any?): Boolean = other is DescriptorSetLayout && bindings == other.bindings

    override fun hashCode(): Int = bindings.hashCode()

    companion object {
        /** Reserves an empty set position without renumbering sets that follow it. */
        val Empty = DescriptorSetLayout()
    }
}
