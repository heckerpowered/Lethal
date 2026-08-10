/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

/**
 * Describes shader modules that are intended to be used together as a set of pipeline stages.
 *
 * Each module contributes the [ShaderStage] declared by [ShaderModule.stage]. A stage may occur at
 * most once, and module order has no semantic significance.
 *
 * This description does not imply that the modules are mutually compatible or supported by the
 * current graphics backend. Those requirements are established when [ShaderStages] is created.
 */
data class ShaderStagesDescription(
    val modules: List<ShaderModule>,
    val label: String,
)