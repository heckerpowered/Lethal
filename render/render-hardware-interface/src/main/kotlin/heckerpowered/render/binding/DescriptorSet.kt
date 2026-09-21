/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.binding

import java.util.*

/**
 * Supplies the actual resources for one group of shader resource slots.
 *
 * A material layout can require an image at binding 0 and a sampler at binding 1. A brick set
 * supplies a brick image and a sampler; a wood set can supply a different image using the same
 * layout. The layout describes what is accepted, while this set selects what a draw will use.
 *
 * The set number is supplied by the bind command, not stored here. Each [DescriptorBinding]
 * names a binding within that set, so the order of the constructor's list does not assign slots.
 * A set completely specifies its group rather than inheriting resources from a previous material.
 * Every declared binding and every element of its fixed-length array must be supplied exactly
 * once. Undeclared bindings, missing resources, and implicit null descriptors are not accepted.
 *
 * Resource selections are immutable. Create another set to change a selection; doing so does
 * not alter draws already recorded with this set. This does not snapshot buffer bytes or texels:
 * the selected resources retain their own contents and command-ordered updates.
 *
 * This is a logical binding value, not a separately allocated GPU resource. Constructing it
 * checks slot structure and basic resource metadata. The recording backend must still verify
 * device identity, resource validity, shader numeric types, alignment, limits, sampler pairing,
 * input-attachment mapping, and synchronization before accepting the corresponding accesses.
 * Any native descriptor storage is managed by the backend, not by closing this value.
 *
 * @throws IllegalArgumentException if bindings do not fill [layout], or a resource's kind, usage,
 * range size, image shape, sample form, or required storage format contradicts its declaration.
 */
class DescriptorSet(
    val layout: DescriptorSetLayout,
    bindings: List<DescriptorBinding>,
    val label: String = "",
) {
    /** Explicit bindings sorted by number; shader array order within each binding is preserved. */
    val bindings: List<DescriptorBinding> = Collections.unmodifiableList(bindings.sortedBy { it.binding })

    init {
        require(this.bindings.zipWithNext().none { (left, right) -> left.binding == right.binding }) { "Descriptor binding numbers must be unique within a set" }
        require(this.bindings.map { it.binding } == layout.bindings.map { it.binding }) { "Descriptor set must supply exactly the bindings declared by its layout" }

        for ((index, binding) in this.bindings.withIndex()) {
            val declaration = layout.bindings[index]
            require(binding.resources.size == declaration.descriptorCount) { "Binding ${binding.binding} requires ${declaration.descriptorCount} resources, but received ${binding.resources.size}" }
            binding.resources.forEachIndexed { element, resource ->
                validateDescriptorResource(declaration.type, resource, "Binding ${binding.binding}[$element]")
            }
        }
    }

    /** Looks up the explicit binding number rather than a position in the declaration list. */
    fun findBinding(binding: Int): DescriptorBinding? = bindings.firstOrNull { it.binding == binding }
}
