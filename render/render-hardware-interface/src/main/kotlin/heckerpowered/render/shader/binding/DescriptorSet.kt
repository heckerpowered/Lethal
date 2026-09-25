/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.binding

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
 * checks slot structure and resource metadata, including the numeric values exposed by image
 * aspects. The recording backend must still verify device identity, resource validity, actual
 * shader declarations, alignment, limits, sampler pairing,
 * input-attachment mapping, and synchronization before accepting the corresponding accesses.
 * Any native descriptor storage is managed by the backend, not by closing this value.
 *
 * @throws IllegalArgumentException if bindings do not fill [layout], or a resource's kind, usage,
 * range size, image shape, sample form, numeric category, or storage format contradicts its declaration.
 */
class DescriptorSet(
    val layout: DescriptorSetLayout,
    bindings: List<DescriptorBinding>,
    val label: String = "",
) {
    /** Explicit bindings sorted by number; shader array order within each binding is preserved. */
    val bindings: List<DescriptorBinding> =
        Collections.unmodifiableList(bindings.sortedBy { it.binding })

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

    /**
     * Checks whether this resource group fits the set declaration expected by a pipeline.
     *
     * A set can be valid for its own layout yet be placed in the wrong pipeline slot. For
     * example, a material image and sampler cannot replace a camera-buffer set. This check
     * requires matching binding numbers, descriptor requirements, fixed array counts, and
     * stage visibility. Equivalent layouts may be distinct objects and have different labels.
     *
     * Matching only the resources would lose part of the contract: a set declared for a
     * smaller buffer range or different stage visibility is not silently relabeled here,
     * even when its current resources happen to satisfy the other declaration.
     *
     * This checks one logical set interface, not native pipeline-layout compatibility or the
     * continued validity of its resources. The backend must establish native bindings and
     * check shader-specific requirements before the set is consumed.
     *
     * @throws IllegalArgumentException if this set's layout differs from [expectedLayout].
     */
    fun validateFor(expectedLayout: DescriptorSetLayout) {
        if (layout == expectedLayout) return

        val actualBindings = layout.bindings.map { it.binding }
        val expectedBindings = expectedLayout.bindings.map { it.binding }
        require(actualBindings == expectedBindings) { "Descriptor set '$label' declares bindings $actualBindings, but the target expects $expectedBindings" }
        for ((actual, expected) in layout.bindings.zip(expectedLayout.bindings)) {
            validateBindingLayout(actual, expected, "Descriptor set '$label', binding ${expected.binding}")
        }
    }

    /** Looks up the explicit binding number rather than a position in the declaration list. */
    fun findBinding(binding: Int): DescriptorBinding? = bindings.firstOrNull { it.binding == binding }
}

private fun validateBindingLayout(actual: DescriptorBindingLayout, expected: DescriptorBindingLayout, context: String) {
    require(actual.type == expected.type) { "$context declares ${actual.type}, but the target requires ${expected.type}" }
    require(actual.descriptorCount == expected.descriptorCount) { "$context declares ${actual.descriptorCount} descriptors, but the target requires ${expected.descriptorCount}" }
    require(actual.stages == expected.stages) { "$context is visible to ${actual.stages}, but the target declares ${expected.stages}" }
}
