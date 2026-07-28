/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

enum class ShaderStage {
    Vertex,
    Fragment,
}

data class ShaderModuleDescription(
    val stage: ShaderStage,
    val source: ShaderSource,
    val label: String = source.label,
)

interface ShaderModule : GpuResource {
    val stage: ShaderStage
}
