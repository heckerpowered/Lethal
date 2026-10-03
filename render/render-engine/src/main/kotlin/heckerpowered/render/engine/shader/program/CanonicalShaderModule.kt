/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.shader.program

import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderSource
import java.util.*

/**
 * Captures one canonical stage's source and include contents for RHI preparation.
 * Origin selects root-relative include resolution independently of diagnostic labels. A shader's
 * source callback must return modules from one stable resource generation, including every include;
 * it must not combine changing resource-manager contents within that source snapshot.
 */
class CanonicalShaderModule(description: ShaderModuleDescription, val origin: String, includes: Map<String, String> = emptyMap()) {
    val description = description.copy(
        code = (description.code as? ShaderSource)?.copy()
            ?: error("Canonical shader preparation requires source code")
    )
    val includes: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(includes))
}
