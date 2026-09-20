/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.texture

/**
 * Reports a backend failure while establishing a texture view, such as an allocation failure.
 *
 * The message should identify the view request and include available backend diagnostics.
 * Invalid selections and unsupported capabilities are reported separately rather than being
 * treated as allocation failures. A failed creation returns no usable view.
 */
class TextureViewCreationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
