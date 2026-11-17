/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.source

data class MemoryShaderSource(val text: String) : ShaderSource {
    private val identity = ShaderFingerprint.of(text)
    override fun fingerprint(): ShaderFingerprint = identity
    override fun load(): String = text
    override fun load(expected: ShaderFingerprint): String? = text.takeIf { expected == identity }
}
