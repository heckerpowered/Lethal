/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.draw

import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.engine.support.collection.toUnmodifiableMap
import heckerpowered.render.shader.binding.DescriptorSet

/** Snapshots shader binding membership; push writes retain their own immutable byte snapshots. */
internal class ShaderBindings(
    descriptorSets: Map<Int, DescriptorSet> = emptyMap(),
    pushConstants: List<PreparedPushConstants> = emptyList(),
) {
    val descriptorSets: Map<Int, DescriptorSet> = descriptorSets.toUnmodifiableMap()
    val pushConstants: List<PreparedPushConstants> = pushConstants.toUnmodifiableList()

    init {
        require(this.descriptorSets.keys.all { it >= 0 }) { "Descriptor set index must not be negative" }
    }
}

internal fun RenderPass.bindBindings(bindings: ShaderBindings) {
    bindings.descriptorSets.forEach { [set, descriptors] -> bindDescriptorSet(set, descriptors) }
    bindings.pushConstants.forEach { pushConstants(it) }
}
