/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.binding

import java.util.*

/**
 * Supplies the resources for one explicitly numbered binding in a descriptor set.
 *
 * For an ordinary slot, provide one resource. For an array declared with descriptorCount = 4,
 * provide four resources in shader array-index order. Their indices are elements of this one
 * binding, not additional binding numbers.
 *
 * The resource list is copied and cannot be changed through this value. Editing the caller's
 * list afterward therefore cannot replace a resource already selected for a draw.
 *
 * @throws IllegalArgumentException if the binding number is negative or the list is empty.
 */
class DescriptorBinding(
    val binding: Int,
    resources: List<DescriptorResource>,
) {
    val resources: List<DescriptorResource> = Collections.unmodifiableList(resources.toList())

    constructor(binding: Int, resource: DescriptorResource) : this(binding, listOf(resource))

    init {
        require(binding >= 0) { "Descriptor binding number must be non-negative" }
        require(this.resources.isNotEmpty()) { "Descriptor binding requires at least one resource" }
    }
}
