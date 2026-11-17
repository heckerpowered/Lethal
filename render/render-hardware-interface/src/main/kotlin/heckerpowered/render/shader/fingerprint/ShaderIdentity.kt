/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.fingerprint

/**
 * Captures the input from which a shader-content fingerprint is generated.
 *
 * [source] is the exact source text from one captured read, not a resource path or diagnostic
 * label. It is not normalized: whitespace, line endings, and every UTF-16 code unit remain part
 * of the identity. The text used for compilation and fingerprinting must come from the same read.
 *
 * This value currently describes source text only. Compiler targets, options, entry points, and
 * include resolution remain separate cache checks; equality here does not prove that two complete
 * compilation requests are interchangeable. Fingerprint generation belongs to
 * [ShaderFingerprintAlgorithm], not to this value.
 */
data class ShaderIdentity(val source: String)
