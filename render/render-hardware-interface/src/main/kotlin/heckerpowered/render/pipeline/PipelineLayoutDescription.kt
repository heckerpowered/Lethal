/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pipeline

import heckerpowered.render.DescriptorSetLayout
import heckerpowered.render.PushConstantLayout

/**
 * Immutable description used to create a [PipelineLayout].
 *
 * The description defines the complete shader-visible resource interface consisting of numbered
 * descriptor sets and, optionally, push constants.
 *
 * For example:
 *
 * ```
 * PipelineLayoutDescription(
 *     descriptorSets = listOf(
 *         sceneLayout,     // set 0
 *         materialLayout,  // set 1
 *     ),
 *     pushConstants = drawConstants,
 *     label = "World",
 * )
 * ```
 *
 * In this example, bindings declared by `sceneLayout` are addressed through set 0, bindings
 * declared by `materialLayout` through set 1, and `drawConstants` describes data supplied directly
 * through push-constant commands.
 */
data class PipelineLayoutDescription(
    /**
     * Descriptor-set layouts available to shaders, ordered by set index.
     *
     * The element at index `n` defines descriptor set `n`. Set indices identify groups of
     * resources, while bindings declared by each [DescriptorSetLayout] identify individual
     * resource slots within that set.
     *
     * For example, `(set = 1, binding = 3)` refers to binding 3 of `descriptorSets[1]`.
     *
     * An empty list means that the layout exposes no descriptor sets.
     */
    val descriptorSets: List<DescriptorSetLayout> = emptyList(),

    /**
     * Layout of the push-constant data accessible to shaders.
     *
     * Push constants provide a small block of values that can be updated directly while encoding
     * commands without allocating or binding a buffer or descriptor set. The layout determines
     * the byte ranges and shader-stage visibility of that data.
     *
     * Typical values include object indices, draw flags, small offsets, or other frequently
     * changing per-draw data.
     *
     * `null` means that the layout exposes no push constants.
     */
    val pushConstants: PushConstantLayout? = null,

    /**
     * Human-readable name used for diagnostics and debugging.
     *
     * The label does not affect resource compatibility or runtime behavior.
     */
    val label: String,
)