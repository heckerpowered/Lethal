/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.binding.DescriptorSet
import heckerpowered.render.shader.binding.DescriptorSetLayout
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
     * Checks a resource group against the declaration at shader set number [set].
     *
     * The set number selects a group, not a binding inside that group. A camera group can
     * therefore be rejected at the material position even though it is internally valid.
     * This method neither changes command state nor requires other set positions to be bound.
     *
     * @throws IllegalArgumentException if [set] is absent or the group's layout does not match.
     */
    fun validateDescriptorSet(set: Int, descriptors: DescriptorSet) {
        require(set in descriptorSets.indices) { "Pipeline layout '$label' has no descriptor set $set" }
        descriptors.validateFor(descriptorSets[set])
    }

    /**
     * Checks the logical resource groups consumed by a particular pipeline's shaders.
     *
     * [requiredSets] must come from the backend's validated interface for the selected shader
     * entry points. Reserving a set in this layout does not mean that those shaders use it.
     * Unused positions, including selections retained from another pipeline, are ignored so
     * a draw is not forced to supply a material group that its shaders never access.
     *
     * This method does not discover shader accesses. It checks that every required position
     * has a selection in [boundSets] and that the selection's complete layout matches. Native
     * descriptor validity, buffer limits, and actual resource accesses require further checks.
     *
     * @throws IllegalArgumentException if a required index is absent from this layout or a
     * selected group's layout does not match its required position.
     * @throws IllegalStateException if a required set has not been selected.
     */
    fun validateDescriptorSets(requiredSets: Set<Int>, boundSets: Map<Int, DescriptorSet>) {
        require(requiredSets.all { it in descriptorSets.indices }) { "Required sets ${requiredSets.filterNot { it in descriptorSets.indices }.sorted()} are absent from pipeline layout '$label'" }
        for (set in requiredSets.sorted()) {
            val descriptors = checkNotNull(boundSets[set]) { "Pipeline layout '$label' requires descriptor set $set, but it is unbound" }
            validateDescriptorSet(set, descriptors)
        }
    }

    /**
     * Checks a parameter write against this pipeline's declared push-constant interface.
     *
     * The recording command obtains this description from its selected pipeline. It must not
     * infer a new interface from the write or treat a missing layout as unrestricted storage.
     * [PushConstantLayout.validateWrite] checks byte bounds and all participating stages.
     * This method does not read the source data or establish that a shader's inputs are initialized.
     *
     * @throws IllegalArgumentException if no push constants are exposed or the write is invalid.
     */
    fun validatePushConstantWrite(stages: Set<ShaderStage>, offsetBytes: Int, sizeBytes: Int) {
        val constants = requireNotNull(pushConstants) {
            "Pipeline layout '$label' exposes no push constants"
        }
        constants.validateWrite(stages, offsetBytes, sizeBytes)
    }

    /**
     * Reports whether parameter values written under one description can be interpreted under
     * the other without changing the declared byte ranges or stage groups.
     *
     * Exact push-constant declarations must match; descriptor-set differences and labels do
     * not matter for this comparison. This only checks interface compatibility. A draw must
     * still have values written for every byte and stage it needs, and later writes may have
     * replaced earlier values. No parameter values are stored by either description.
     */
    fun isPushConstantCompatibleWith(other: PipelineLayoutDescription): Boolean =
        pushConstants == other.pushConstants

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