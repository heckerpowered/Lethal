/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.fingerprint

/**
 * Converts a captured [ShaderIdentity] into a fingerprint for content-reuse checks.
 *
 * Implementations define the encoding and comparison domain of their results. Every field of
 * [ShaderIdentity] must participate: adding identity fields requires updating each algorithm,
 * rather than continuing to hash only the source text. Equivalent identities must produce
 * matching fingerprints within the same algorithm domain.
 *
 * Loading source text and selecting compiler settings are caller responsibilities. The algorithm
 * consumes the supplied snapshot and must not reread its origin while generating a fingerprint.
 */
fun interface ShaderFingerprintAlgorithm {
    /**
     * Produces an immutable fingerprint of the complete supplied [identity].
     *
     * The result must retain its meaning independently of later source changes and subsequent
     * calls. It is compared through [ShaderFingerprint.matches], not concrete digest fields.
     */
    fun fingerprint(identity: ShaderIdentity): ShaderFingerprint

    companion object {
        /**
         * Uses [Sha256ShaderFingerprintAlgorithm] for sources that do not select another algorithm.
         */
        val Default: ShaderFingerprintAlgorithm = Sha256ShaderFingerprintAlgorithm
    }
}
