/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader.source

import heckerpowered.render.shader.fingerprint.ShaderFingerprint

/**
 * A place to obtain text, independently of includes and compilation.
 * [fingerprint] returns null when the current identity cannot be obtained reliably without
 * loading the content. That does not prevent caching the identity returned by [load].
 *
 * Each read must return text and its matching identity together. Implementations using mutable
 * storage must establish that correspondence themselves; checking metadata before reading is
 * insufficient. This contract does not establish a transaction across different sources.
 * I/O failures are thrown. Conditional loading returns null only when identities do not match.
 */
interface ShaderSource {
    fun fingerprint(): ShaderFingerprint?
    fun load(): ShaderSourceContent
    fun load(expected: ShaderFingerprint): ShaderSourceContent? =
        load().takeIf { expected.matches(it.fingerprint) }
}
