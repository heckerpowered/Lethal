/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.shader

import heckerpowered.render.GraphicsDevice

/**
 * Human-readable shader source code that must be compiled by the graphics backend.
 *
 * [language] identifies the source language and determines how [text] is interpreted. The source
 * must already contain any preprocessing results required by the backend, unless preprocessing is
 * explicitly provided elsewhere by the RHI implementation.
 *
 * A source object does not represent a compiled GPU resource. Compilation occurs when it is passed
 * to [GraphicsDevice.createShaderModule].
 */
data class ShaderSource(
    /**
     * Language in which [text] is written.
     */
    val language: ShaderLanguage,

    /**
     * Complete shader source text supplied to the backend compiler.
     *
     * This should be the final source representation expected by the backend, including any
     * required declarations, version directives, generated definitions, and entry point.
     */
    val text: String,

    /**
     * Human-readable name used for compilation diagnostics and debugging.
     *
     * This commonly identifies the source file or generated shader variant. It does not affect
     * compilation semantics.
     */
    override val label: String,
) : ShaderCode