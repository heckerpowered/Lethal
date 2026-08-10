/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.shader.ShaderModuleDescription

class ShaderModuleCreationException(
    val description: ShaderModuleDescription,
    val diagnostics: String,
) : RuntimeException(
    buildString {
        append("Failed to create ")
        append(description.stage)
        append(" shader module \"")
        append(description.label)
        append("\" with entry point \"")
        append(description.entryPoint)
        append('"')

        if (diagnostics.isNotBlank()) {
            append(":\n")
            append(diagnostics)
        }
    },
)