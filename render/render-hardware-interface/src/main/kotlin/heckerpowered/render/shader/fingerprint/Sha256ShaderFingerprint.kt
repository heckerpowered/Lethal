/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.fingerprint

internal class Sha256ShaderFingerprint(private val value: String) : ShaderFingerprint {
    override fun matches(other: ShaderFingerprint): Boolean = other is Sha256ShaderFingerprint && value == other.value
}
