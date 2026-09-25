/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Describes shader modules requested to form one set of pipeline stages.
 *
 * A typical description combines a vertex module with a fragment module so that both can be
 * established as the programmable stages of a render pipeline.
 *
 * Constructing or copying this description only records references to the selected modules. It
 * does not validate the combination, establish a [ShaderStages], or keep the modules open.
 * Validation and establishment occur when a graphics device creates the [ShaderStages].
 */
data class ShaderStagesDescription(
    /**
     * Shader modules requested for the stage combination.
     *
     * Each module contributes the [ShaderStage] declared by [ShaderModule.stage]. Module order has
     * no semantic significance, but the same stage may not occur more than once. For example, one
     * combination cannot contain two vertex modules.
     */
    val modules: List<ShaderModule>,

    /**
     * Human-readable name for the resulting stage combination.
     *
     * The label does not need to be unique and is used only for diagnostics, debugging, and device
     * object naming. It does not affect shader execution.
     */
    val label: String,
)