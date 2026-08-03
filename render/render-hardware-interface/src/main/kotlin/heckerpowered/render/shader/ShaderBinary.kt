/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import java.nio.ByteBuffer

/**
 * Binary representation of shader code accepted by a graphics backend.
 *
 * A shader binary is not necessarily native GPU machine code. Intermediate representations such
 * as SPIR-V may still require validation, specialization, translation, optimization, or driver
 * compilation before a pipeline can execute them.
 *
 * [format] defines how [bytes] must be interpreted. Compatibility with a particular backend,
 * device, driver, shader stage, or entry point depends on that format and is validated during
 * shader-module or pipeline creation.
 */
data class ShaderBinary(
    /**
     * Format of the binary shader representation stored in [bytes].
     */
    val format: ShaderBinaryFormat,

    /**
     * Complete binary shader representation.
     *
     * The byte sequence must satisfy the structural, alignment, and encoding requirements of
     * [format]. It must not be modified while a shader-module creation operation is reading it.
     */
    val bytes: ByteBuffer,

    /**
     * Human-readable name used for validation diagnostics and debugging.
     *
     * This commonly identifies the originating source, cache entry, or compiled shader variant.
     * It does not affect shader behavior.
     */
    override val label: String,
) : ShaderCode