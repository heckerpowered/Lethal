/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.fingerprint

import java.security.MessageDigest

/**
 * Produces SHA-256 fingerprints of the source text captured in [ShaderIdentity].
 *
 * Each UTF-16 code unit contributes its high byte followed by its low byte, without a byte-order
 * mark or text normalization. Encoding code units directly preserves lone surrogates that a
 * charset encoder could replace. Empty text is valid and hashes the empty byte sequence.
 *
 * Results match only this implementation's fingerprint type with the same digest. They do not
 * match another algorithm's result merely because its digest bytes are identical. As with other
 * fixed-size hashes, content-reuse decisions rely on collision resistance rather than a proof
 * that distinct inputs can never collide.
 *
 * Each call owns its digest state, so concurrent calls do not share mutable hashing state.
 */
object Sha256ShaderFingerprintAlgorithm : ShaderFingerprintAlgorithm {
    override fun fingerprint(identity: ShaderIdentity): ShaderFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        // Charset encoders can replace lone surrogates, losing source identity.
        for (character in identity.source) {
            digest.update((character.code ushr 8).toByte())
            digest.update(character.code.toByte())
        }
        return Sha256ShaderFingerprint(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
    }
}
