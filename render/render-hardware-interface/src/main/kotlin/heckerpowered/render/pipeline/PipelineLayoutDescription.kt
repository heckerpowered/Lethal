/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.binding.DescriptorSetLayout
import java.util.*

/**
 * Describes the resource interface used to create a [PipelineLayout].
 *
 * Shaders can access resources such as camera buffers and material textures, and can also read
 * small values supplied directly for each draw. [descriptorSets] declares the resource
 * slots; [pushConstants] declares the directly supplied parameter ranges. Together they define
 * the interface against which shader declarations and application-provided inputs are checked.
 *
 * This is a device-independent request. Creating the layout establishes device support;
 * combining it with shader stages establishes shader compatibility. The description does not
 * supply actual resources, parameter values, or shader code.
 */
class PipelineLayoutDescription(
    descriptorSets: List<DescriptorSetLayout> = emptyList(),
    pushConstants: PushConstantLayout? = null,

    /**
     * Diagnostic name for the layout. It does not affect resource addressing or structural equality.
     */
    val label: String,
) {
    /**
     * Resource-group layouts in shader set-number order.
     *
     * For example, a renderer can put camera resources in set 0 and material resources in set 1:
     *
     * ```
     * descriptorSets[0] -> scene layout
     *     binding 0    -> camera uniform buffer
     *
     * descriptorSets[1] -> material layout
     *     binding 0    -> sampled texture
     *     binding 1    -> sampler
     * ```
     *
     * A shader address `(set = 1, binding = 0)` selects the material texture slot. The list index
     * chooses the set; the explicit binding number in that set's layout chooses the slot.
     * Changing a material supplies different resources to the same slots instead of changing
     * this interface. These entries describe slot requirements, not the resources currently bound.
     *
     * Use [DescriptorSetLayout.Empty] to leave a set position empty without renumbering later
     * sets. Reusing the same layout at two list positions declares two distinct set addresses.
     * An empty list exposes no descriptor sets. The list is copied and cannot be modified.
     */
    val descriptorSets: List<DescriptorSetLayout> = Collections.unmodifiableList(descriptorSets.toList())

    /**
     * Layout of the small parameter block supplied directly by push-constant commands.
     *
     * Use it for values such as a per-object transform, color, object index, or draw flags that
     * can change between draws without replacing a descriptor set. Each range declares which
     * bytes particular shader stages may read; the shader block defines the members at those
     * byte offsets, and recorded commands supply their values.
     *
     * This parameter block is separate from the numbered descriptor sets. It has byte offsets
     * rather than `(set, binding)` addresses, and assigning this property does not write values.
     * See [PushConstantRange] for a matching shader block and [PushConstantLayout] for the
     * relationship between declarations and updates.
     *
     * `null` exposes no push constants. An empty layout is normalized to `null` so both ways of
     * requesting no push constants have the same representation.
     */
    val pushConstants: PushConstantLayout? = pushConstants?.takeIf { it.ranges.isNotEmpty() }

    /**
     * Compares ordered set layouts and exact push-constant declarations, ignoring diagnostic labels.
     *
     * This compares resource-interface descriptions, not the complete compatibility of pipelines
     * that may use them.
     */
    override fun equals(other: Any?): Boolean = other is PipelineLayoutDescription &&
            descriptorSets == other.descriptorSets && pushConstants == other.pushConstants

    override fun hashCode(): Int = 31 * descriptorSets.hashCode() + pushConstants.hashCode()
}
