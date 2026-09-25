/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader.binding

import heckerpowered.render.shader.ShaderStage
import java.util.*

/**
 * Declares one numbered resource slot, or a fixed-length array of slots, within a descriptor set.
 *
 * For example, binding 3 can declare four sampled textures visible to the fragment shader.
 * The shader addresses them as elements 0 through 3 of binding 3. They do not become bindings
 * 3 through 6, and the declaration's position in a Kotlin list does not assign its binding number.
 *
 * The containing pipeline layout assigns the set number. Actual resources are supplied later;
 * declaring a slot does not allocate a buffer, texture, sampler, or descriptor set.
 *
 * @throws IllegalArgumentException if the binding is negative, the count or visibility is empty,
 * or an input attachment is made visible to a stage other than Fragment.
 */
class DescriptorBindingLayout(
    val binding: Int,
    val type: DescriptorType,
    stages: Set<ShaderStage>,
    val descriptorCount: Int = 1,
) {
    /**
     * Shader stages permitted to access this binding.
     *
     * Visibility declares access, not execution order or synchronization. A stage omitted here
     * cannot use the slot even if another stage uses an identically named shader variable.
     */
    val stages: Set<ShaderStage> = Collections.unmodifiableSet(stages.toSet())

    init {
        require(binding >= 0) { "Descriptor binding number must be non-negative" }
        require(descriptorCount > 0) { "Descriptor count must be positive" }
        require(this.stages.isNotEmpty()) { "Descriptor binding requires shader-stage visibility" }
        require(type !is DescriptorType.InputAttachment || this.stages == setOf(ShaderStage.Fragment)) {
            "Input attachments are visible only to the fragment stage"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is DescriptorBindingLayout && binding == other.binding && type == other.type &&
                stages == other.stages && descriptorCount == other.descriptorCount

    override fun hashCode(): Int {
        var result = binding
        result = 31 * result + type.hashCode()
        result = 31 * result + stages.hashCode()
        return 31 * result + descriptorCount
    }
}
