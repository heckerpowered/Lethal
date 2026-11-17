/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.fingerprint

/**
 * Represents captured shader content so a cache can check whether that content is unchanged.
 *
 * A fingerprint is an immutable comparison result, not a source loader or a hashing strategy.
 * [ShaderFingerprintAlgorithm] produces fingerprints from [ShaderIdentity]; callers compare them
 * through [matches] without depending on the digest representation.
 *
 * Matching covers only the represented identity. Compiler settings and dependency resolution
 * still need their own checks before compiled artifacts can be reused. A source-scoped revision
 * may represent content, but a path, timestamp, or diagnostic name alone cannot establish it.
 */
interface ShaderFingerprint {
    /**
     * Returns whether [other] represents the same content within a compatible identity domain.
     *
     * Comparison performs no I/O and must remain stable, symmetric, and transitive. Fingerprints
     * from incompatible algorithms or identity domains return false, even when their stored bytes
     * happen to be equal. Implementations must not intentionally reuse an identity for changed
     * content; digest-based implementations additionally rely on their collision resistance.
     */
    fun matches(other: ShaderFingerprint): Boolean
}
